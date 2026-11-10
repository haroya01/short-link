package com.example.short_link.link.stats.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.exception.LinkException;
import com.example.short_link.link.stats.application.read.LinkStatsQueryService;
import com.example.short_link.link.stats.domain.repository.ClickEventRepository;
import com.example.short_link.link.stats.domain.repository.ClickRollupRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
class ClickExclusionIntegrationTest {

  private static final Instant NOW = Instant.parse("2026-10-20T06:37:13Z");
  private static final Instant TODAY = Instant.parse("2026-10-20T00:00:00Z");
  private static final Instant CREATED = NOW.minus(Duration.ofDays(30)).minusSeconds(4_321);
  private static final int DAYS = 20;
  private static final String EXCLUDED = "preview:excluded";

  @Autowired private ClickExclusionService exclusions;
  @Autowired private ClickRollupService rollup;
  @Autowired private ClickRollupRepository rollups;
  @Autowired private ClickEventRepository clickEvents;
  @Autowired private LinkStatsQueryService stats;
  @Autowired private UserRepository users;
  @Autowired private LinkRepository links;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private JsonMapper json;
  @MockitoSpyBean private Clock clock;

  private final List<Long> linkIds = new ArrayList<>();
  private final ExecutorService background = Executors.newSingleThreadExecutor();
  private Long owner;
  private Long admin;

  @BeforeEach
  void freezeTheReportTime() {
    when(clock.instant()).thenReturn(NOW);
    LocaleContextHolder.setLocale(Locale.ENGLISH);
    clearRollups();
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    owner =
        users.save(new UserEntity("clk-" + suffix + "@x.com", "google", "g-clk" + suffix)).getId();
    admin =
        users.save(new UserEntity("adm-" + suffix + "@x.com", "google", "g-adm" + suffix)).getId();
  }

  @AfterEach
  void removeTheSeed() {
    background.shutdownNow();
    LocaleContextHolder.resetLocaleContext();
    clearRollups();
    for (Long linkId : linkIds) {
      jdbc.update(
          "DELETE FROM audit_log WHERE target_id = (SELECT short_code FROM link WHERE id = ?)",
          linkId);
      jdbc.update("DELETE FROM click_event WHERE link_id = ?", linkId);
      jdbc.update("DELETE FROM link WHERE id = ?", linkId);
    }
    jdbc.update("DELETE FROM users WHERE id IN (?, ?)", owner, admin);
  }

  @Test
  void aClickExcludedOnADayStillReadFromRawReadsAsAPreviewFromTheStartAndRestoresExactly() {
    List<Click> recorded = clicks();
    List<Integer> picked = humansWhere(recorded, click -> !click.at().isBefore(TODAY));
    Seeded link = seed(recorded, List.of());
    Seeded twin = seed(recorded, picked);
    rollup.aggregate(NOW);
    JsonNode asRecorded = report(link);
    JsonNode asPreviewFromTheStart = report(twin);
    assertThat(asRecorded).isNotEqualTo(asPreviewFromTheStart);
    assertThat(asRecorded.get("lastClickAt"))
        .isNotEqualTo(asPreviewFromTheStart.get("lastClickAt"));

    assertThat(exclusions.exclude(admin, link.code(), link.ids(picked))).isEqualTo(picked.size());
    assertThat(report(link)).isEqualTo(asPreviewFromTheStart);

    assertThat(exclusions.restore(admin, link.code(), link.ids(picked))).isEqualTo(picked.size());
    assertThat(report(link)).isEqualTo(asRecorded);
  }

  @Test
  void aClickExcludedOnADayAlreadySummedReadsAsAPreviewFromTheStartAndRestoresExactly() {
    List<Click> recorded = clicks();
    List<Integer> picked = humansWhere(recorded, ClickExclusionIntegrationTest::onAPickedSummedDay);
    Seeded link = seed(recorded, List.of());
    Seeded twin = seed(recorded, picked);
    rollup.aggregate(NOW);
    JsonNode asRecorded = report(link);
    JsonNode asPreviewFromTheStart = report(twin);
    assertThat(asRecorded).isNotEqualTo(asPreviewFromTheStart);
    assertThat(asRecorded.get("firstClickAt"))
        .isNotEqualTo(asPreviewFromTheStart.get("firstClickAt"));
    Map<String, Object> othersSums = recentSums(twin.linkId());

    assertThat(exclusions.exclude(admin, link.code(), link.ids(picked))).isEqualTo(picked.size());
    assertThat(report(link)).isEqualTo(asPreviewFromTheStart);
    assertThat(recentSums(twin.linkId())).isEqualTo(othersSums);

    assertThat(exclusions.restore(admin, link.code(), link.ids(picked))).isEqualTo(picked.size());
    assertThat(report(link)).isEqualTo(asRecorded);
    assertThat(recentSums(twin.linkId())).isEqualTo(othersSums);
  }

  @Test
  void theNightlySumAfterAnExclusionNeitherLosesItNorCountsItTwice() {
    List<Click> recorded = clicks();
    List<Integer> picked =
        humansWhere(recorded, click -> onAPickedSummedDay(click) || !click.at().isBefore(TODAY));
    Seeded link = seed(recorded, List.of());
    Seeded twin = seed(recorded, picked);
    rollup.aggregate(NOW);
    exclusions.exclude(admin, link.code(), link.ids(picked));
    JsonNode excluded = report(link);
    assertThat(excluded).isEqualTo(report(twin));

    rollup.aggregate(NOW);
    assertThat(report(link)).isEqualTo(excluded);

    rollup.aggregate(NOW.plus(Duration.ofDays(1)));
    assertThat(aggregatedUntil()).isEqualTo(TODAY.plus(Duration.ofDays(1)));
    assertThat(report(link)).isEqualTo(excluded);

    clearRollups();
    assertThat(report(link)).isEqualTo(excluded);
  }

  @Test
  void excludingLeavesAnotherLinksClickAlone() {
    List<Click> recorded = clicks();
    Seeded link = seed(recorded, List.of());
    Seeded other = seed(recorded, List.of());
    int human = humansWhere(recorded, click -> true).getFirst();
    long mine = link.id(human);
    long theirs = other.id(human);

    assertThat(exclusions.exclude(admin, link.code(), List.of(mine, theirs, 9_999_999_999L)))
        .isEqualTo(1);

    assertThat(botName(mine)).isEqualTo(EXCLUDED);
    assertThat(botName(theirs)).isNull();
  }

  @Test
  void excludingLeavesBotClicksAndAlreadyExcludedClicksAlone() {
    List<Click> recorded = clicks();
    Seeded link = seed(recorded, List.of());
    long crawler = link.id(firstWhere(recorded, click -> "crawler:x".equals(click.botName())));
    long preview =
        link.id(firstWhere(recorded, click -> "preview:slackbot".equals(click.botName())));
    long human = link.id(humansWhere(recorded, click -> true).getFirst());
    assertThat(exclusions.exclude(admin, link.code(), List.of(human))).isEqualTo(1);
    String before = rows(link);

    assertThat(exclusions.exclude(admin, link.code(), List.of(crawler, preview, human))).isZero();

    assertThat(rows(link)).isEqualTo(before);
    assertThat(botName(crawler)).isEqualTo("crawler:x");
    assertThat(botName(preview)).isEqualTo("preview:slackbot");
  }

  @Test
  void restoringLeavesAnotherLinksExcludedClickAlone() {
    List<Click> recorded = clicks();
    Seeded link = seed(recorded, List.of());
    Seeded other = seed(recorded, List.of());
    int human = humansWhere(recorded, click -> true).getFirst();
    relabel(link.id(human), EXCLUDED);
    relabel(other.id(human), EXCLUDED);

    assertThat(exclusions.restore(admin, link.code(), List.of(link.id(human), other.id(human))))
        .isEqualTo(1);

    assertThat(botName(link.id(human))).isNull();
    assertThat(botName(other.id(human))).isEqualTo(EXCLUDED);
  }

  @Test
  void restoringLeavesOperatorVisitsOtherPreviewsBotsAndPeopleAlone() {
    List<Click> recorded = clicks();
    Seeded link = seed(recorded, List.of());
    List<Integer> humans = humansWhere(recorded, click -> true);
    long operator = link.id(humans.get(0));
    long prefetch = link.id(humans.get(1));
    long person = link.id(humans.get(2));
    long crawler = link.id(firstWhere(recorded, click -> "crawler:x".equals(click.botName())));
    relabel(operator, "preview:operator");
    relabel(prefetch, "preview:prefetch");
    String before = rows(link);

    assertThat(
            exclusions.restore(
                admin, link.code(), List.of(operator, prefetch, person, crawler, 9_999_999_999L)))
        .isZero();

    assertThat(rows(link)).isEqualTo(before);
  }

  @Test
  void aClickAlreadyFoldedIntoTheRollupsIsNotExcluded() {
    List<Click> recorded = clicks();
    Seeded link = seed(recorded, List.of());
    long folded = link.id(humansWhere(recorded, click -> daysAgo(click) == DAYS - 1).getFirst());
    foldDaysBefore(TODAY.minus(Duration.ofDays(DAYS - 2)));

    assertThat(exclusions.exclude(admin, link.code(), List.of(folded))).isZero();

    assertThat(botName(folded)).isNull();
  }

  @Test
  void anExcludedClickAlreadyFoldedIntoTheRollupsIsNotRestored() {
    List<Click> recorded = clicks();
    Seeded link = seed(recorded, List.of());
    long folded = link.id(humansWhere(recorded, click -> daysAgo(click) == DAYS - 1).getFirst());
    relabel(folded, EXCLUDED);
    foldDaysBefore(TODAY.minus(Duration.ofDays(DAYS - 2)));

    assertThat(exclusions.restore(admin, link.code(), List.of(folded))).isZero();

    assertThat(botName(folded)).isEqualTo(EXCLUDED);
  }

  @Test
  void aFoldedClickNamedBesideALiveOneDoesNotBringItsDayBackIntoTheRecentSums() {
    List<Click> recorded = clicks();
    Seeded link = seed(recorded, List.of());
    long folded = link.id(humansWhere(recorded, click -> daysAgo(click) == DAYS - 1).getFirst());
    long live = link.id(humansWhere(recorded, click -> daysAgo(click) == 7).getFirst());
    Instant rolledUntil = TODAY.minus(Duration.ofDays(DAYS - 2));
    rollup.aggregate(NOW);
    foldDaysBefore(rolledUntil);

    assertThat(exclusions.exclude(admin, link.code(), List.of(folded, live))).isPositive();

    assertThat(botName(live)).isEqualTo(EXCLUDED);
    assertThat(
            jdbc.queryForObject(
                "SELECT (SELECT COUNT(*) FROM click_recent_quarter WHERE link_id = ?"
                    + " AND quarter_start < FROM_UNIXTIME(?)) + (SELECT COUNT(*)"
                    + " FROM click_recent_dim WHERE link_id = ? AND day_start < FROM_UNIXTIME(?))",
                Long.class,
                link.linkId(),
                rolledUntil.getEpochSecond(),
                link.linkId(),
                rolledUntil.getEpochSecond()))
        .isZero();
  }

  @Test
  void eachRequestLeavesOneAuditRowNamingTheAdminTheLinkAndTheCount() {
    List<Click> recorded = clicks();
    Seeded link = seed(recorded, List.of());
    List<Integer> picked = humansWhere(recorded, click -> !click.at().isBefore(TODAY));
    List<Long> asked = new ArrayList<>(link.ids(picked));
    asked.add(9_999_999_999L);

    exclusions.exclude(admin, link.code(), asked);
    exclusions.restore(admin, link.code(), asked);

    assertThat(
            jdbc.queryForList(
                "SELECT action, actor_user_id, target_type, CAST(metadata AS CHAR) AS metadata"
                    + " FROM audit_log WHERE target_id = ? ORDER BY id",
                link.code().value()))
        .extracting(
            row -> row.get("action"),
            row -> ((Number) row.get("actor_user_id")).longValue(),
            row -> row.get("target_type"),
            row -> json.readTree((String) row.get("metadata")))
        .containsExactly(
            Tuple.tuple("LINK_CLICKS_EXCLUDED", admin, "link", counts(asked.size(), picked.size())),
            Tuple.tuple(
                "LINK_CLICKS_RESTORED", admin, "link", counts(asked.size(), picked.size())));
  }

  @Test
  void anUnknownLinkIsRefusedBeforeAnythingIsChangedOrLogged() {
    assertThatThrownBy(() -> exclusions.exclude(admin, new ShortCode("nolink404"), List.of(1L)))
        .isInstanceOf(LinkException.class);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE target_id = 'nolink404'", Long.class))
        .isZero();
  }

  @Test
  void anExclusionArrivingWhileItsDayIsBeingSummedWaitsAndThenSumsThatDayAgain() throws Exception {
    List<Click> recorded = clicks();
    Instant day = TODAY.minus(Duration.ofDays(1));
    List<Integer> picked = humansWhere(recorded, click -> daysAgo(click) == 1);
    Seeded link = seed(recorded, List.of());
    Seeded twin = seed(recorded, picked);
    rollup.aggregate(day.plus(Duration.ofHours(2)));
    assertThat(aggregatedUntil()).isEqualTo(day);
    CountDownLatch summed = new CountDownLatch(1);
    Future<Integer> exclusion =
        background.submit(
            () -> {
              summed.await();
              return exclusions.exclude(admin, link.code(), link.ids(picked));
            });

    readCommitted()
        .executeWithoutResult(
            status -> {
              rollups.lockState();
              rollups.aggregateDay(day, TODAY);
              summed.countDown();
              awaitUntil(() -> exclusion.isDone() || waitingForTheStateRow());
              rollups.advanceAggregatedTo(TODAY);
            });

    assertThat(exclusion.get(20, TimeUnit.SECONDS)).isEqualTo(picked.size());
    assertThat(report(link)).isEqualTo(report(twin));
  }

  @Test
  void aNightlySumArrivingWhileAnExclusionIsInFlightWaitsAndSumsWhatTheExclusionLeft()
      throws Exception {
    List<Click> recorded = clicks();
    List<Integer> picked = humansWhere(recorded, click -> daysAgo(click) == DAYS - 1);
    Seeded link = seed(recorded, List.of());
    Seeded twin = seed(recorded, picked);
    CountDownLatch locked = new CountDownLatch(1);
    Future<Integer> nightly =
        background.submit(
            () -> {
              locked.await();
              return rollup.aggregate(NOW);
            });

    readCommitted()
        .executeWithoutResult(
            status -> {
              ClickRollupRepository.State state = rollups.lockState();
              locked.countDown();
              awaitUntil(() -> nightly.isDone() || waitingForTheStateRow());
              clickEvents.markHumansAs(
                  link.linkId(), link.ids(picked), EXCLUDED, state.rolledUntil());
            });

    assertThat(nightly.get(20, TimeUnit.SECONDS)).isPositive();
    assertThat(aggregatedUntil()).isEqualTo(TODAY);
    assertThat(report(link)).isEqualTo(report(twin));
  }

  private TransactionTemplate readCommitted() {
    TransactionTemplate template = new TransactionTemplate(transactionManager);
    template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    return template;
  }

  private boolean waitingForTheStateRow() {
    return jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.processlist"
                + " WHERE id <> CONNECTION_ID() AND info LIKE '%click_rollup_state%'",
            Long.class)
        > 0;
  }

  private static void awaitUntil(BooleanSupplier condition) {
    long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
    while (!condition.getAsBoolean()) {
      if (System.nanoTime() > deadline) throw new AssertionError("nothing reached the state row");
      try {
        Thread.sleep(20);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        throw new AssertionError(interrupted);
      }
    }
  }

  private static boolean onAPickedSummedDay(Click click) {
    long daysAgo = daysAgo(click);
    return daysAgo == DAYS - 1 || daysAgo == 7 || daysAgo == 1;
  }

  private static long daysAgo(Click click) {
    return Duration.between(click.at().truncatedTo(ChronoUnit.DAYS), TODAY).toDays();
  }

  private JsonNode counts(int requested, int changed) {
    return json.createObjectNode().put("requested", requested).put("changed", changed);
  }

  private void foldDaysBefore(Instant rolledUntil) {
    jdbc.update(
        "DELETE FROM click_recent_quarter WHERE quarter_start < FROM_UNIXTIME(?)",
        rolledUntil.getEpochSecond());
    jdbc.update(
        "DELETE FROM click_recent_dim WHERE day_start < FROM_UNIXTIME(?)",
        rolledUntil.getEpochSecond());
    jdbc.update(
        "UPDATE click_rollup_state SET rolled_until = FROM_UNIXTIME(?), aggregated_until ="
            + " GREATEST(aggregated_until, FROM_UNIXTIME(?)) WHERE id = 1",
        rolledUntil.getEpochSecond(),
        rolledUntil.getEpochSecond());
  }

  private String botName(long clickId) {
    return jdbc.queryForObject(
        "SELECT IF(is_bot = 1, bot_name, NULL) FROM click_event WHERE id = ?",
        String.class,
        clickId);
  }

  private void relabel(long clickId, String botName) {
    jdbc.update("UPDATE click_event SET is_bot = 1, bot_name = ? WHERE id = ?", botName, clickId);
  }

  private String rows(Seeded... seeded) {
    StringBuilder all = new StringBuilder();
    for (Seeded link : seeded) {
      all.append(
          jdbc.queryForList(
              "SELECT id, is_bot, bot_name FROM click_event WHERE link_id = ? ORDER BY id",
              link.linkId()));
    }
    return all.toString();
  }

  private Map<String, Object> recentSums(Long linkId) {
    return jdbc.queryForMap(
        "SELECT (SELECT CONCAT_WS('/', COUNT(*), SUM(humans), SUM(bots),"
            + " SUM(UNIX_TIMESTAMP(first_human_at)), SUM(UNIX_TIMESTAMP(last_human_at)))"
            + " FROM click_recent_quarter WHERE link_id = ?) AS quarters,"
            + " (SELECT CONCAT_WS('/', COUNT(*), SUM(clicks), SUM(is_bot),"
            + " SUM(UNIX_TIMESTAMP(first_at))) FROM click_recent_dim WHERE link_id = ?) AS dims",
        linkId,
        linkId);
  }

  private Instant aggregatedUntil() {
    return Instant.ofEpochSecond(
        jdbc.queryForObject(
            "SELECT UNIX_TIMESTAMP(aggregated_until) FROM click_rollup_state WHERE id = 1",
            Long.class));
  }

  private JsonNode report(Seeded link) {
    ObjectNode report = (ObjectNode) json.valueToTree(stats.stats(owner, link.code()));
    report.remove(List.of("shortCode", "velocity"));
    return canonical(report);
  }

  private JsonNode canonical(JsonNode node) {
    if (node instanceof ObjectNode object) {
      object.properties().forEach(e -> object.set(e.getKey(), canonical(e.getValue())));
      return object;
    }
    if (node instanceof ArrayNode array) {
      List<JsonNode> items = new ArrayList<>();
      array.forEach(item -> items.add(canonical(item)));
      List<JsonNode> sorted = new ArrayList<>();
      int start = 0;
      while (start < items.size()) {
        int end = start + 1;
        while (end < items.size() && sameCount(items.get(start), items.get(end))) end++;
        List<JsonNode> run = new ArrayList<>(items.subList(start, end));
        run.sort(Comparator.comparing(JsonNode::toString));
        sorted.addAll(run);
        start = end;
      }
      ArrayNode out = json.createArrayNode();
      sorted.forEach(out::add);
      return out;
    }
    return node;
  }

  private static boolean sameCount(JsonNode a, JsonNode b) {
    return a.has("count") && b.has("count") && a.get("count").equals(b.get("count"));
  }

  private static List<Integer> humansWhere(List<Click> clicks, Predicate<Click> wanted) {
    List<Integer> picked = new ArrayList<>();
    for (int i = 0; i < clicks.size(); i++) {
      if (!clicks.get(i).bot() && wanted.test(clicks.get(i))) {
        picked.add(i);
      }
    }
    assertThat(picked).isNotEmpty();
    return picked;
  }

  private static int firstWhere(List<Click> clicks, Predicate<Click> wanted) {
    for (int i = 0; i < clicks.size(); i++) {
      if (wanted.test(clicks.get(i))) return i;
    }
    throw new AssertionError("no such click in the seed");
  }

  private static List<Click> clicks() {
    String[] referrers = {null, "https://news.ycombinator.com/item", "https://www.google.com/"};
    String[] hosts = {null, "news.ycombinator.com", "www.google.com"};
    String[] devices = {"desktop", "mobile", null};
    String[][] regions = {
      {"KR", "Seoul", "KR-11"}, {"IN", "Karnataka", "IN-KA"}, {null, null, null}
    };
    String[] channels = {null, "profile-links", "newsletter"};
    String[] bots = {"crawler:x", "preview:slackbot", "datacenter:AWS"};
    int[] secondsOfDay = {37, 3_601, 20_700, 20_703, 23_000, 23_640, 40_000, 61_234, 86_399};
    List<Click> clicks = new ArrayList<>();
    int n = 0;
    for (int daysAgo = DAYS - 1; daysAgo >= 0; daysAgo--) {
      Instant dayStart = TODAY.minus(Duration.ofDays(daysAgo));
      for (int second : secondsOfDay) {
        Instant at = dayStart.plusSeconds(second);
        if (!at.isBefore(NOW.minusSeconds(120))) continue;
        n++;
        boolean bot = n % 5 == 0;
        String[] region = regions[n % regions.length];
        clicks.add(
            new Click(
                at,
                bot,
                bot ? bots[(n / 5) % bots.length] : null,
                referrers[n % referrers.length],
                hosts[n % hosts.length],
                devices[n % devices.length],
                n % 2 == 0 ? "iOS" : "Android",
                n % 4 == 0 ? "Safari" : "Chrome",
                n % 3 == 0 ? "launch" : null,
                n % 7 == 0 ? "kakaotalk" : null,
                channels[n % channels.length],
                region[0],
                region[1],
                region[2],
                n % 2 == 0 ? "ko" : "en",
                n % 6 == 0 ? null : "visitor-" + (n % 11),
                n % 4 == 0 ? 16509 : 4766,
                n % 4 == 0 ? "AMAZON-02" : "Korea Telecom"));
      }
    }
    return clicks;
  }

  private Seeded seed(List<Click> recorded, List<Integer> previewsFromTheStart) {
    String code = "x" + UUID.randomUUID().toString().substring(0, 7);
    LinkEntity link =
        links.save(new LinkEntity("https://example.com/exclusion", code, owner, null));
    jdbc.update(
        "UPDATE link SET created_at = TIMESTAMPADD(SECOND, ?, '1970-01-01') WHERE id = ?",
        BigDecimal.valueOf(CREATED.toEpochMilli(), 3),
        link.getId());
    linkIds.add(link.getId());
    List<Object[]> rows = new ArrayList<>();
    for (int i = 0; i < recorded.size(); i++) {
      Click click = recorded.get(i);
      boolean preview = previewsFromTheStart.contains(i);
      rows.add(
          new Object[] {
            link.getId(),
            click.at().getEpochSecond(),
            preview || click.bot() ? 1 : 0,
            preview ? EXCLUDED : click.botName(),
            click.referrer(),
            click.referrerHost(),
            click.device(),
            click.os(),
            click.browser(),
            click.utmCampaign(),
            click.clientApp(),
            click.sourceChannel(),
            click.country(),
            click.region(),
            click.regionCode(),
            click.language(),
            click.visitor(),
            click.asn(),
            click.asnOrg(),
          });
    }
    jdbc.batchUpdate(
        "INSERT INTO click_event (link_id, clicked_at, is_bot, bot_name, referrer, referrer_host,"
            + " device_class, os_name, browser_name, utm_campaign, client_app, source_channel,"
            + " country_code, region_name, region_code, language, visitor_hash, asn, asn_org,"
            + " client_ip) VALUES (?, FROM_UNIXTIME(?), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,"
            + " ?, ?, '10.0.0.0')",
        rows);
    List<Long> ids =
        jdbc.queryForList(
            "SELECT id FROM click_event WHERE link_id = ? ORDER BY id", Long.class, link.getId());
    assertThat(ids).hasSameSizeAs(recorded);
    return new Seeded(link.getId(), link.getShortCode(), ids);
  }

  private void clearRollups() {
    jdbc.update("DELETE FROM click_hourly");
    jdbc.update("DELETE FROM click_daily_dim");
    jdbc.update("DELETE FROM click_daily_visitors");
    jdbc.update("DELETE FROM click_recent_quarter");
    jdbc.update("DELETE FROM click_recent_dim");
    jdbc.update(
        "UPDATE click_rollup_state SET rolled_until = FROM_UNIXTIME(1),"
            + " aggregated_until = FROM_UNIXTIME(1) WHERE id = 1");
  }

  private record Seeded(Long linkId, ShortCode code, List<Long> clickIds) {

    List<Long> ids(List<Integer> positions) {
      return positions.stream().map(clickIds::get).toList();
    }

    long id(int position) {
      return clickIds.get(position);
    }
  }

  private record Click(
      Instant at,
      boolean bot,
      String botName,
      String referrer,
      String referrerHost,
      String device,
      String os,
      String browser,
      String utmCampaign,
      String clientApp,
      String sourceChannel,
      String country,
      String region,
      String regionCode,
      String language,
      String visitor,
      Integer asn,
      String asnOrg) {}
}
