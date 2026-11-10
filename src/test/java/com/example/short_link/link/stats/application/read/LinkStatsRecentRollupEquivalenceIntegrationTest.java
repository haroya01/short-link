package com.example.short_link.link.stats.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.short_link.link.classifier.application.helper.ReferrerNormalizer;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.stats.application.ClickRollupService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
class LinkStatsRecentRollupEquivalenceIntegrationTest {

  private static final Instant NOW = Instant.parse("2026-10-20T06:37:13Z");
  private static final Instant DAILY_FROM = NOW.minus(Duration.ofDays(30));
  private static final List<String> ZONES =
      List.of(
          "Asia/Seoul", "America/New_York", "Asia/Kolkata", "Asia/Kathmandu", "Australia/Adelaide");

  @Autowired private LinkStatsQueryService stats;
  @Autowired private UserRepository users;
  @Autowired private LinkRepository links;
  @Autowired private ClickRollupService rollup;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private JsonMapper json;
  @MockitoSpyBean private Clock clock;

  private final List<Long> userIds = new ArrayList<>();
  private final List<Long> linkIds = new ArrayList<>();
  private final Map<String, Long> owners = new LinkedHashMap<>();
  private final Map<String, ShortCode> codes = new LinkedHashMap<>();
  private final Map<String, Long> linkByZone = new LinkedHashMap<>();

  @BeforeEach
  void freezeTheReportTime() {
    when(clock.instant()).thenReturn(NOW);
    LocaleContextHolder.setLocale(Locale.ENGLISH);
    clearRollups();
  }

  @AfterEach
  void removeTheSeed() {
    LocaleContextHolder.resetLocaleContext();
    clearRollups();
    for (Long linkId : linkIds) {
      jdbc.update("DELETE FROM click_event WHERE link_id = ?", linkId);
      jdbc.update("DELETE FROM link WHERE id = ?", linkId);
    }
    for (Long userId : userIds) {
      jdbc.update("DELETE FROM users WHERE id = ?", userId);
    }
  }

  @Test
  void aReportReadsTheSameFromSummedDaysAsFromRawClicksAlone() {
    Instant created = NOW.minus(Duration.ofDays(60)).minusSeconds(4_321).minusMillis(789);
    for (int i = 0; i < ZONES.size(); i++) {
      seedLink(ZONES.get(i), created, new Random(20261103L + i));
    }
    JsonNode rawOnly = reports();

    assertThat(rollup.aggregate(NOW)).isPositive();
    assertThat(aggregatedUntil()).isEqualTo(Instant.parse("2026-10-20T00:00:00Z"));
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM click_recent_quarter", Long.class))
        .isPositive();

    assertThat(reports()).isEqualTo(rawOnly);
  }

  @Test
  void aClickFlushedAfterItsDayWasSummedCountsOnceTheNextRunSumsThatDayAgain() {
    seedLink("Asia/Seoul", NOW.minus(Duration.ofDays(20)), new Random(7));
    rollup.aggregate(NOW);
    Long linkId = linkByZone.get("Asia/Seoul");
    jdbc.update(
        "INSERT INTO click_event (link_id, clicked_at, is_bot, client_ip, visitor_hash)"
            + " VALUES (?, FROM_UNIXTIME(?), 0, '10.0.0.0', 'late')",
        linkId,
        Instant.parse("2026-10-19T23:59:59Z").getEpochSecond());

    rollup.aggregate(NOW);
    JsonNode summed = reports();
    reopenFrom(Instant.EPOCH);

    assertThat(summed).isEqualTo(reports());
  }

  @Test
  void reclassifyingSummedClicksReadsRightOnceTheirDaysAreReopened() {
    seedLink("Asia/Kolkata", NOW.minus(Duration.ofDays(40)), new Random(11));
    rollup.aggregate(NOW);
    Instant day = Instant.parse("2026-10-01T00:00:00Z");
    jdbc.update(
        "UPDATE click_event SET is_bot = 1, bot_name = 'datacenter:Reclassified'"
            + " WHERE link_id = ? AND clicked_at >= FROM_UNIXTIME(?) AND clicked_at < FROM_UNIXTIME(?)",
        linkByZone.get("Asia/Kolkata"),
        day.getEpochSecond(),
        day.plus(Duration.ofDays(1)).getEpochSecond());

    reopenFrom(day);
    JsonNode reopened = reports();
    rollup.aggregate(NOW);
    JsonNode summedAgain = reports();
    reopenFrom(Instant.EPOCH);
    JsonNode rawOnly = reports();

    assertThat(reopened).isEqualTo(rawOnly);
    assertThat(summedAgain).isEqualTo(rawOnly);
  }

  @Test
  void aReportReadsTheSameOnBothSidesOfTheRolledTheSummedAndTheWindowBoundaries() {
    Instant created = NOW.minus(Duration.ofDays(100)).minusSeconds(777).minusMillis(250);
    Instant rolledUntil = NOW.minus(Duration.ofDays(90)).truncatedTo(ChronoUnit.DAYS);
    Instant aggregatedUntil = Instant.parse("2026-10-20T00:00:00Z");
    List<Instant> edges = List.of(rolledUntil, aggregatedUntil, DAILY_FROM.plusSeconds(467));
    for (String zone : List.of("Asia/Seoul", "America/New_York", "Asia/Kolkata")) {
      seedLink(zone, created, new Random(zone.hashCode()), edges, false);
    }
    JsonNode rawOnly = summable(reports());

    assertThat(rollup.rollUp(NOW)).isPositive();
    assertThat(rollup.deleteRolledUp()).isPositive();
    JsonNode rolled = summable(reports());
    assertThat(rollup.aggregate(NOW)).isPositive();
    assertThat(aggregatedUntil()).isEqualTo(aggregatedUntil);
    JsonNode summed = summable(reports());

    assertThat(rolled).isEqualTo(rawOnly);
    assertThat(summed).isEqualTo(rawOnly);
  }

  private static JsonNode summable(JsonNode reports) {
    reports
        .properties()
        .forEach(
            zone -> {
              ObjectNode report = (ObjectNode) zone.getValue();
              report.remove(List.of("uniqueClicks", "rawSince", "returnRate", "insights"));
              report
                  .get("channelDepth")
                  .forEach(
                      depth ->
                          ((ObjectNode) depth).remove(List.of("returningVisitors", "returnRatio")));
              if (zone.getKey().equals("Asia/Kolkata")) {
                report.remove(List.of("heatmap", "hourClicks", "dayOfWeekClicks", "peakHour"));
              }
            });
    return reports;
  }

  private void reopenFrom(Instant day) {
    jdbc.update(
        "DELETE FROM click_recent_quarter WHERE quarter_start >= FROM_UNIXTIME(?)",
        day.getEpochSecond());
    jdbc.update(
        "DELETE FROM click_recent_dim WHERE day_start >= FROM_UNIXTIME(?)", day.getEpochSecond());
    jdbc.update(
        "UPDATE click_rollup_state SET aggregated_until ="
            + " GREATEST(rolled_until, LEAST(aggregated_until, FROM_UNIXTIME(?))) WHERE id = 1",
        Math.max(1, day.getEpochSecond()));
  }

  private Instant aggregatedUntil() {
    return Instant.ofEpochSecond(
        jdbc.queryForObject(
            "SELECT UNIX_TIMESTAMP(aggregated_until) FROM click_rollup_state WHERE id = 1",
            Long.class));
  }

  private JsonNode reports() {
    ObjectNode reports = json.createObjectNode();
    for (String zone : owners.keySet()) {
      JsonNode report = json.valueToTree(stats.stats(owners.get(zone), codes.get(zone)));
      ((ObjectNode) report).remove(List.of("shortCode", "velocity"));
      reports.set(zone, canonical(report));
    }
    return reports;
  }

  private JsonNode canonical(JsonNode node) {
    if (node instanceof ObjectNode object) {
      object.properties().forEach(e -> object.set(e.getKey(), canonical(e.getValue())));
      return object;
    }
    if (node instanceof ArrayNode array) {
      List<JsonNode> items = new ArrayList<>();
      array.forEach(item -> items.add(labelsFolded(canonical(item))));
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

  private static JsonNode labelsFolded(JsonNode item) {
    if (item instanceof ObjectNode row && row.has("count")) {
      row.properties().stream()
          .filter(e -> e.getValue().isString())
          .toList()
          .forEach(e -> row.put(e.getKey(), e.getValue().asString().toLowerCase(Locale.ROOT)));
    }
    return item;
  }

  private static boolean sameCount(JsonNode a, JsonNode b) {
    return a.has("count") && b.has("count") && a.get("count").equals(b.get("count"));
  }

  private void seedLink(String zone, Instant created, Random random) {
    seedLink(zone, created, random, List.of(), true);
  }

  private void seedLink(
      String zone, Instant backdated, Random random, List<Instant> edges, boolean creationSecond) {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    UserEntity user = new UserEntity("recent-" + suffix + "@x.com", "google", "g-rc" + suffix);
    user.changeTimezone(zone);
    Long owner = users.save(user).getId();
    userIds.add(owner);
    String code = "q" + UUID.randomUUID().toString().substring(0, 7);
    LinkEntity link = links.save(new LinkEntity("https://example.com/" + code, code, owner, null));
    jdbc.update(
        "UPDATE link SET created_at = TIMESTAMPADD(SECOND, ?, '1970-01-01') WHERE id = ?",
        BigDecimal.valueOf(backdated.toEpochMilli(), 3),
        link.getId());
    Instant created = links.findByShortCode(link.getShortCode()).orElseThrow().getCreatedAt();
    linkIds.add(link.getId());
    owners.put(zone, owner);
    codes.put(zone, link.getShortCode());
    linkByZone.put(zone, link.getId());

    ZoneId zoneId = ZoneId.of(zone);
    long span = Duration.between(created, NOW).minusHours(1).getSeconds();
    List<Instant> times = new ArrayList<>();
    for (int i = 0; i < 300; i++) {
      times.add(created.plusSeconds((long) (random.nextDouble() * span)));
    }
    if (creationSecond) times.add(created);
    times.add(created.plusSeconds(1));
    for (Instant edge : edges) {
      times.addAll(List.of(edge.minusSeconds(1), edge, edge.plusSeconds(1)));
    }
    for (int seconds : new int[] {-1, 0, 1, 466, 467}) {
      times.add(DAILY_FROM.plusSeconds(seconds));
    }
    for (int daysAgo : new int[] {29, 16, 2, 1}) {
      LocalDate local = NOW.minus(Duration.ofDays(daysAgo)).atZone(zoneId).toLocalDate();
      Instant localMidnight = local.atStartOfDay(zoneId).toInstant();
      Instant utcMidnight = local.atStartOfDay(ZoneOffset.UTC).toInstant();
      times.addAll(
          List.of(
              localMidnight.minusSeconds(1),
              localMidnight,
              utcMidnight.minusSeconds(1),
              utcMidnight));
    }
    Instant sessions = NOW.minus(Duration.ofDays(3));
    List<Object[]> rows = new ArrayList<>();
    for (Instant at : times) {
      if (at.isBefore(created.minusSeconds(1)) || !at.isBefore(NOW)) continue;
      rows.add(row(link.getId(), at, random, pick(random, VISITORS)));
    }
    int[][] gaps = {{0, 29}, {0, 30}, {0, 31}, {0, 45}};
    for (int v = 0; v < gaps.length; v++) {
      for (int minute : gaps[v]) {
        rows.add(row(link.getId(), sessions.plus(Duration.ofMinutes(minute)), random, "gap-" + v));
      }
    }
    jdbc.batchUpdate(
        "INSERT INTO click_event (link_id, clicked_at, is_bot, bot_name, referrer, referrer_host,"
            + " device_class, os_name, browser_name, utm_campaign, utm_source, client_app,"
            + " fetch_site, source_channel, country_code, region_name, region_code, city_name,"
            + " language, visitor_hash, asn, asn_org, destination_id, post_id, client_ip) VALUES (?,"
            + " FROM_UNIXTIME(?), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,"
            + " '10.0.0.0')",
        rows);
  }

  private static Object[] row(Long linkId, Instant at, Random random, String visitor) {
    boolean bot = random.nextInt(8) == 0;
    String referrer = pick(random, REFERRERS);
    String[] region = pick(random, REGIONS);
    String[] asn = pick(random, ASNS);
    return new Object[] {
      linkId,
      at.getEpochSecond(),
      bot ? 1 : 0,
      bot ? pick(random, BOT_NAMES) : pick(random, HUMAN_BOT_NAMES),
      ReferrerNormalizer.normalize(referrer),
      ReferrerNormalizer.hostOf(referrer),
      pick(random, Arrays.asList(null, "desktop", "mobile", "tablet")),
      pick(random, Arrays.asList(null, "iOS", "Android", "Windows")),
      pick(random, Arrays.asList(null, "Safari", "Chrome")),
      pick(random, Arrays.asList(null, null, "newsletter", "Newsletter")),
      pick(random, Arrays.asList(null, null, "twitter", "kakao")),
      pick(random, Arrays.asList(null, null, "kakaotalk", "instagram")),
      pick(random, Arrays.asList(null, "cross-site", "none")),
      pick(random, Arrays.asList(null, null, "profile-links", "Profile-card")),
      region[0],
      region[1],
      region[2],
      pick(random, Arrays.asList(null, "Gangnam", "Shibuya")),
      pick(random, Arrays.asList(null, "ko", "ja", "en")),
      bot ? null : visitor,
      asn[0] == null ? null : Integer.valueOf(asn[0]),
      asn[1],
      pick(random, Arrays.asList(null, null, null, 9_100_001L)),
      pick(random, Arrays.asList(null, null, null, null, 9_200_001L)),
    };
  }

  private static final List<String> REFERRERS =
      Arrays.asList(
          null,
          null,
          "https://news.ycombinator.com/item",
          "https://www.google.com/",
          "https://l.instagram.com/",
          "https://Blog.Example.org/post/2",
          "https://blog.example.org/post/2");

  private static final List<String[]> REGIONS =
      List.of(
          new String[] {null, null, null},
          new String[] {"KR", "Seoul", "KR-11"},
          new String[] {"KR", "Seoul", null},
          new String[] {"IN", "Karnataka", "IN-KA"},
          new String[] {"AU", "South Australia", "AU-SA"});

  private static final List<String[]> ASNS =
      List.of(
          new String[] {null, null},
          new String[] {"4766", "Korea Telecom"},
          new String[] {"16509", "AMAZON-02"});

  private static final List<String> BOT_NAMES =
      Arrays.asList(null, "preview:slackbot", "Preview:Discordbot", "datacenter:AWS", "crawler:x");

  private static final List<String> HUMAN_BOT_NAMES =
      Arrays.asList(null, null, null, null, null, "datacenter:Relay");

  private static final List<String> VISITORS = visitors();

  private static List<String> visitors() {
    List<String> pool = new ArrayList<>();
    for (int i = 0; i < 60; i++) pool.add("visitor-" + i);
    for (int i = 0; i < 15; i++) pool.add(null);
    return pool;
  }

  private static <T> T pick(Random random, List<T> values) {
    return values.get(random.nextInt(values.size()));
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
}
