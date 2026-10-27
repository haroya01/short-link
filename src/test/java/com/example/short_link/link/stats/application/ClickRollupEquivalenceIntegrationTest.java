package com.example.short_link.link.stats.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.admin.application.read.AdminMetricReader;
import com.example.short_link.admin.domain.repository.AdminAnalyticsRepository;
import com.example.short_link.admin.domain.repository.AdminBrowseRepository;
import com.example.short_link.admin.domain.repository.AdminMetricsRepository;
import com.example.short_link.campaign.application.CampaignBatchService;
import com.example.short_link.campaign.application.CampaignStatsService;
import com.example.short_link.campaign.application.dto.BatchWithLink;
import com.example.short_link.campaign.application.write.CreateCampaignUseCase;
import com.example.short_link.campaign.domain.CampaignEntity;
import com.example.short_link.campaign.presentation.request.CampaignBatchCreateRequest;
import com.example.short_link.campaign.presentation.request.CampaignCreateRequest;
import com.example.short_link.link.application.dto.LinkStats;
import com.example.short_link.link.classifier.application.helper.ReferrerNormalizer;
import com.example.short_link.link.classifier.application.helper.VisitorHasher;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.stats.application.read.ClickTotalsReader;
import com.example.short_link.link.stats.application.read.LinkStatsQueryService;
import com.example.short_link.link.stats.domain.repository.ClickEventRepository;
import com.example.short_link.link.stats.domain.repository.ClickRollupRepository;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostLinkClickReader;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import java.lang.reflect.RecordComponent;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("test")
class ClickRollupEquivalenceIntegrationTest {

  private static final Set<String> NINETY_DAY_FIELDS =
      Set.of(
          "uniqueClicks", "rawSince", "returnRate", "insights", "returningVisitors", "returnRatio");

  @Autowired private ClickRollupService rollup;
  @Autowired private ClickRollupRepository rollupRepository;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private LinkStatsQueryService linkStats;
  @Autowired private CampaignStatsService campaignStats;
  @Autowired private CampaignBatchService batchService;
  @Autowired private CreateCampaignUseCase createCampaign;
  @Autowired private ClickTotalsReader totals;
  @Autowired private ClickEventRepository clickEvents;
  @Autowired private PostLinkClickReader postClicks;
  @Autowired private AdminMetricsRepository adminMetrics;
  @Autowired private AdminMetricReader adminMetricReader;
  @Autowired private AdminAnalyticsRepository adminAnalytics;
  @Autowired private AdminBrowseRepository adminBrowse;
  @Autowired private UserRepository users;
  @Autowired private LinkRepository links;
  @Autowired private PostRepository posts;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManagerFactory emf;

  private final List<Long> userIds = new ArrayList<>();
  private final List<Long> linkIds = new ArrayList<>();
  private final List<Long> postIds = new ArrayList<>();
  private final List<Long> campaignIds = new ArrayList<>();
  private final Map<Long, Long> ownerByLink = new LinkedHashMap<>();
  private final Map<Long, ShortCode> codeByLink = new LinkedHashMap<>();

  @BeforeEach
  @AfterEach
  void resetRollup() {
    jdbc.update("DELETE FROM click_hourly");
    jdbc.update("DELETE FROM click_daily_dim");
    jdbc.update("DELETE FROM click_daily_visitors");
    jdbc.update("UPDATE click_rollup_state SET rolled_until = FROM_UNIXTIME(1) WHERE id = 1");
  }

  @AfterEach
  void removeSeed() {
    for (Long linkId : linkIds) {
      jdbc.update("DELETE FROM click_event WHERE link_id = ?", linkId);
    }
    for (Long campaignId : campaignIds) {
      jdbc.update("DELETE FROM campaign_batch WHERE campaign_id = ?", campaignId);
      jdbc.update("DELETE FROM campaign WHERE id = ?", campaignId);
    }
    for (Long linkId : linkIds) {
      jdbc.update("DELETE FROM link WHERE id = ?", linkId);
    }
    for (Long postId : postIds) {
      jdbc.update("DELETE FROM posts WHERE id = ?", postId);
    }
    for (Long userId : userIds) {
      jdbc.update("DELETE FROM users WHERE id = ?", userId);
    }
  }

  @Test
  void everyReadGivesTheSameAnswerAfterOldClicksMoveIntoTheRollups() {
    Instant now = Instant.now();
    Instant created = now.minus(Duration.ofDays(200)).minusSeconds(37_217);
    Long seoul = owner("Asia/Seoul");
    Long newYork = owner("America/New_York");
    Long utc = owner("UTC");
    Long post = post(seoul);
    Random random = new Random(20261022);
    for (Long owner : List.of(seoul, newYork, utc)) {
      Long linkId = link(owner, created);
      seedClicks(linkId, created, now, 500, post, random);
    }
    Instant startsAt = now.minus(Duration.ofDays(150)).truncatedTo(ChronoUnit.HOURS);
    Long campaignLink = campaign(seoul, startsAt, now, created);
    seedClicks(campaignLink, created, now, 300, null, random);

    Map<String, Object> before = snapshot(post, seoul, now);
    Map<Long, Long> uniqueBefore = uniques();

    int days = rollup.rollUp(now);
    assertThat(snapshot(post, seoul, now)).isEqualTo(before);
    assertThat(uniques()).isEqualTo(uniqueBefore);
    long deleted = rollup.deleteRolledUp();

    Instant rolledUntil = rollupRepository.rolledUntil();
    assertThat(rolledUntil).isEqualTo(now.minus(Duration.ofDays(90)).truncatedTo(ChronoUnit.DAYS));
    assertThat(days).isPositive();
    assertThat(deleted).isPositive();
    assertThat(rawRowsBefore(rolledUntil)).isZero();
    assertThat(snapshot(post, seoul, now)).isEqualTo(before);
    assertThat(uniques()).isEqualTo(uniqueBefore);
    for (Long linkId : linkIds) {
      LinkStats.ReturnRate returnRate = stats(linkId).returnRate();
      assertThat(returnRate.newVisitors() + returnRate.returningVisitors())
          .isEqualTo(rawVisitors(linkId));
      assertThat(stats(linkId).rawSince()).isEqualTo(rolledUntil);
    }
  }

  @Test
  void rollingUpAgainTheSameNightChangesNothing() {
    Instant now = Instant.now();
    Instant created = now.minus(Duration.ofDays(120));
    Long owner = owner("Asia/Seoul");
    Long linkId = link(owner, created);
    seedClicks(linkId, created, now, 200, null, new Random(7));
    rollup.rollUp(now);
    rollup.deleteRolledUp();
    Map<String, Object> once = snapshot(null, owner, now);

    assertThat(rollup.rollUp(now)).isZero();
    assertThat(rollup.deleteRolledUp()).isZero();
    assertThat(snapshot(null, owner, now)).isEqualTo(once);
  }

  @Test
  void clicksOlderThanTheBoundaryDayLeaveTheRawTableAndYoungerOnesStay() {
    Instant now = Instant.now();
    Instant boundary = now.minus(Duration.ofDays(90)).truncatedTo(ChronoUnit.DAYS);
    Long owner = owner("Asia/Seoul");
    Long linkId = link(owner, now.minus(Duration.ofDays(100)));
    Instant day89 = now.minus(Duration.ofDays(89)).truncatedTo(ChronoUnit.SECONDS);
    Instant day91 = now.minus(Duration.ofDays(91)).truncatedTo(ChronoUnit.SECONDS);
    for (Instant at : List.of(day89, boundary, boundary.minusSeconds(1), day91)) {
      insertClick(linkId, at);
    }

    rollup.rollUp(now);
    rollup.deleteRolledUp();

    assertThat(rawClickTimes(linkId)).containsExactlyInAnyOrder(boundary, day89);
    assertThat(
            jdbc.queryForObject(
                "SELECT SUM(humans) FROM click_hourly WHERE link_id = ?", Long.class, linkId))
        .isEqualTo(2L);
    assertThat(stats(linkId).humanClicks()).isEqualTo(4);
    assertThat(stats(linkId).firstClickAt()).isEqualTo(day91);
    assertThat(stats(linkId).lastClickAt()).isEqualTo(day89);
  }

  @Test
  void theDeleteWalksTheRolledUpRowsInBatches() {
    Instant now = Instant.now();
    Long owner = owner("Asia/Seoul");
    Long linkId = link(owner, now.minus(Duration.ofDays(120)));
    for (int i = 0; i < 5; i++) {
      insertClick(linkId, now.minus(Duration.ofDays(100)).plusSeconds(i));
    }
    insertClick(linkId, now.minus(Duration.ofDays(1)));
    ClickRollupService twoAtATime =
        new ClickRollupService(
            rollupRepository, new ClickRetentionProperties(90, true, 2), transactionManager);

    twoAtATime.rollUp(now);
    assertThat(twoAtATime.deleteRolledUp()).isEqualTo(5);
    assertThat(rawClickTimes(linkId)).hasSize(1);
    assertThat(stats(linkId).humanClicks()).isEqualTo(6);
  }

  @Test
  void aReportReadsTheVisitorSketchesOnlyForLinksWithRolledUpVisitors() {
    Instant now = Instant.now();
    Long owner = owner("Asia/Seoul");
    Long linkId = link(owner, now.minus(Duration.ofDays(120)));
    seedClicks(linkId, now.minus(Duration.ofDays(120)), now, 100, null, new Random(3));
    long rawOnly = statementsFor(linkId);

    rollup.rollUp(now);
    rollup.deleteRolledUp();

    assertThat(statementsFor(linkId)).isEqualTo(rawOnly + 2);
  }

  private long statementsFor(Long linkId) {
    Statistics statistics = emf.unwrap(SessionFactory.class).getStatistics();
    statistics.clear();
    stats(linkId);
    return statistics.getPrepareStatementCount();
  }

  private Map<String, Object> snapshot(Long post, Long author, Instant now) {
    Map<String, Object> s = new LinkedHashMap<>();
    for (Long linkId : linkIds) {
      s.put("stats:" + linkId, normalize(stats(linkId)));
    }
    s.put("counts", rows(totals.countsByLinkIds(linkIds), r -> r.getLinkId() + "=" + r.getCount()));
    s.put(
        "humans",
        rows(totals.humanCountsByLinkIds(linkIds), r -> r.getLinkId() + "=" + r.getCount()));
    s.put(
        "apps",
        rows(
            clickEvents.humanClientAppCountsByLinkIds(linkIds).entrySet(),
            e ->
                e.getKey()
                    + "="
                    + rows(e.getValue().entrySet(), a -> a.getKey() + ":" + a.getValue())));
    s.put("all", clickEvents.count());
    for (Long campaignId : campaignIds) {
      s.put(
          "campaign:" + campaignId,
          normalize(campaignStats.statsFor(campaignId, ownerOfCampaign(campaignId))));
    }
    if (post != null) {
      Instant since = now.minus(Duration.ofDays(120)).truncatedTo(ChronoUnit.DAYS);
      s.put("post", postClicks.countByPostId(post));
      s.put("postSince", postClicks.countByPostIdSince(post, since));
      s.put("author", postClicks.countByUserId(author));
      s.put("authorSince", postClicks.countByUserIdSince(author, since));
      s.put("postLinks", normalize(postClicks.breakdownByPostId(post, 10)));
    }
    s.put("adminTotal", adminMetrics.totalClicks());
    s.put("adminClickless", adminMetrics.linksWithoutClicks());
    s.put(
        "adminLinks",
        rows(
            adminMetrics.linkMetricRowsByShortCodes(codeByLink.values()),
            r -> r.getShortCode() + "=" + r.getTotalRedirects() + "@" + r.getLastRedirectAt()));
    s.put(
        "adminTopLinks",
        rows(
            adminMetricReader.topLinksByClicks(0, 500).items(),
            r -> r.getShortCode() + "=" + r.getCount()));
    s.put(
        "adminTopUsers",
        rows(
            adminMetricReader.topUsersByClicks(0, 500).items(),
            r -> r.getUserId() + "=" + r.getCount()));
    s.put(
        "adminBrowse",
        rows(
            adminBrowse
                .findLinks(null, author, AdminBrowseRepository.LinkSort.CLICKS, 0, 50)
                .items(),
            r -> r.getShortCode() + "=" + r.getClickCount()));
    s.put(
        "adminLifecycle",
        rows(
            adminAnalytics.lifecycleDays(90),
            r -> r.getDay() + "=" + r.getClicks() + "/" + r.getLinks()));
    s.put(
        "adminMonthly",
        rows(
            adminAnalytics.monthlyActiveUsers(now.minus(Duration.ofDays(366))),
            r -> r.getBucket() + "=" + r.getActive()));
    return s;
  }

  private Map<Long, Long> uniques() {
    Map<Long, Long> uniques = new LinkedHashMap<>();
    linkIds.forEach(id -> uniques.put(id, stats(id).uniqueClicks()));
    return uniques;
  }

  private LinkStats stats(Long linkId) {
    return linkStats.stats(ownerByLink.get(linkId), codeByLink.get(linkId));
  }

  private Long ownerOfCampaign(Long campaignId) {
    return jdbc.queryForObject(
        "SELECT owner_id FROM campaign WHERE id = ?", Long.class, campaignId);
  }

  private long rawRowsBefore(Instant instant) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM click_event WHERE clicked_at < FROM_UNIXTIME(?)",
        Long.class,
        instant.getEpochSecond());
  }

  private long rawVisitors(Long linkId) {
    return jdbc.queryForObject(
        "SELECT COUNT(DISTINCT visitor_hash) FROM click_event WHERE link_id = ? AND is_bot = 0",
        Long.class,
        linkId);
  }

  private List<Instant> rawClickTimes(Long linkId) {
    return jdbc
        .queryForList(
            "SELECT UNIX_TIMESTAMP(clicked_at) FROM click_event WHERE link_id = ?",
            Long.class,
            linkId)
        .stream()
        .map(Instant::ofEpochSecond)
        .toList();
  }

  private static <T> List<String> rows(
      Collection<T> rows, java.util.function.Function<T, String> f) {
    return rows.stream().map(f).sorted().toList();
  }

  private static Object normalize(Object value) {
    if (value instanceof Record record) {
      Map<String, Object> fields = new LinkedHashMap<>();
      for (RecordComponent component : record.getClass().getRecordComponents()) {
        if (NINETY_DAY_FIELDS.contains(component.getName())) continue;
        try {
          fields.put(component.getName(), normalize(component.getAccessor().invoke(record)));
        } catch (ReflectiveOperationException e) {
          throw new IllegalStateException(e);
        }
      }
      return fields;
    }
    if (value instanceof Collection<?> items) {
      return items.stream()
          .map(ClickRollupEquivalenceIntegrationTest::normalize)
          .sorted(Comparator.comparing(String::valueOf))
          .toList();
    }
    return value;
  }

  private Long owner(String timezone) {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    UserEntity user = new UserEntity("rollup-" + suffix + "@x.com", "google", "g-r" + suffix);
    user.changeTimezone(timezone);
    Long id = users.save(user).getId();
    userIds.add(id);
    return id;
  }

  private Long post(Long author) {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    Long id = posts.save(new PostEntity(author, "rollup-" + suffix, "Rollup", "ko")).getId();
    postIds.add(id);
    return id;
  }

  private Long link(Long owner, Instant created) {
    String code = "r" + UUID.randomUUID().toString().substring(0, 7);
    LinkEntity link = links.save(new LinkEntity("https://example.com/" + code, code, owner, null));
    backdate(link.getId(), created);
    track(link.getId(), owner, link.getShortCode());
    return link.getId();
  }

  private Long campaign(Long owner, Instant startsAt, Instant now, Instant created) {
    CampaignEntity campaign =
        createCampaign.execute(
            new CampaignCreateRequest(
                    "Rollup",
                    startsAt,
                    now.plus(Duration.ofDays(1)),
                    "https://example.com/c",
                    null,
                    null,
                    null)
                .toCommand(owner));
    campaignIds.add(campaign.getId());
    BatchWithLink batch =
        batchService.create(
            campaign.getId(),
            owner,
            new CampaignBatchCreateRequest("east", "A", "East", 100, null, null).toCommand());
    Long linkId = batch.link().linkId().value();
    backdate(linkId, created);
    track(linkId, owner, batch.link().shortCode());
    return linkId;
  }

  private void track(Long linkId, Long owner, ShortCode code) {
    linkIds.add(linkId);
    ownerByLink.put(linkId, owner);
    codeByLink.put(linkId, code);
  }

  private void backdate(Long linkId, Instant created) {
    jdbc.update(
        "UPDATE link SET created_at = FROM_UNIXTIME(?) WHERE id = ?",
        created.getEpochSecond(),
        linkId);
  }

  private static final List<String> REFERRERS =
      java.util.Arrays.asList(
          null,
          "https://news.ycombinator.com/item",
          "https://www.google.com/",
          "https://x.com/someone/status/1",
          "https://blog.example.org/post/2");

  private void seedClicks(
      Long linkId, Instant created, Instant now, int count, Long post, Random random) {
    long span = Duration.between(created, now).minusHours(1).getSeconds();
    List<Object[]> rows = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      Instant at = created.plusSeconds((long) (random.nextDouble() * span));
      boolean bot = random.nextInt(10) == 0;
      String referrer = pick(random, REFERRERS);
      String visitor =
          VisitorHasher.hash(linkId, "10.0.0." + random.nextInt(40), "Mozilla/5.0 test");
      rows.add(
          new Object[] {
            linkId,
            at.getEpochSecond(),
            bot ? 1 : 0,
            bot
                ? pick(
                    random,
                    java.util.Arrays.asList("preview:slack", "datacenter:AWS", "googlebot", null))
                : null,
            ReferrerNormalizer.normalize(referrer),
            ReferrerNormalizer.hostOf(referrer),
            pick(random, java.util.Arrays.asList(null, "desktop", "mobile", "tablet")),
            pick(random, java.util.Arrays.asList(null, "iOS", "Android", "Windows")),
            pick(random, java.util.Arrays.asList(null, "Safari", "Chrome")),
            pick(random, java.util.Arrays.asList(null, null, "newsletter", "launch")),
            pick(random, java.util.Arrays.asList(null, null, "twitter", "kakao")),
            pick(random, java.util.Arrays.asList(null, null, "social")),
            pick(random, java.util.Arrays.asList(null, null, "hero")),
            pick(random, java.util.Arrays.asList(null, null, "spring")),
            pick(random, java.util.Arrays.asList(null, "kakaotalk", "instagram")),
            pick(random, java.util.Arrays.asList(null, "cross-site", "none")),
            post != null && random.nextBoolean() ? post : null,
            pick(random, java.util.Arrays.asList(null, "profile-links", "qr")),
            pick(random, java.util.Arrays.asList(null, 9_100_001L, 9_100_002L)),
            pick(random, java.util.Arrays.asList(null, "KR", "JP", "US")),
            pick(random, java.util.Arrays.asList(null, "Seoul", "Tokyo")),
            pick(random, java.util.Arrays.asList(null, "Gangnam", "Shibuya")),
            pick(random, java.util.Arrays.asList(null, "ko", "ja", "en")),
            random.nextInt(4) == 0 ? null : visitor,
            pick(random, java.util.Arrays.asList(null, 4766, 16509)),
          });
    }
    jdbc.batchUpdate(
        "INSERT INTO click_event (link_id, clicked_at, is_bot, bot_name, referrer, referrer_host,"
            + " device_class, os_name, browser_name, utm_campaign, utm_source, utm_medium,"
            + " utm_content, utm_term, client_app, fetch_site, post_id, source_channel,"
            + " destination_id, country_code, region_name, city_name, language, visitor_hash,"
            + " asn, asn_org) VALUES (?, FROM_UNIXTIME(?), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,"
            + " ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CASE ? WHEN 4766 THEN 'Korea Telecom'"
            + " WHEN 16509 THEN 'AMAZON-02' END)",
        rows.stream()
            .map(
                r -> {
                  Object[] withOrg = java.util.Arrays.copyOf(r, r.length + 1);
                  withOrg[r.length] = r[r.length - 1];
                  return withOrg;
                })
            .toList());
  }

  private void insertClick(Long linkId, Instant at) {
    jdbc.update(
        "INSERT INTO click_event (link_id, clicked_at, is_bot) VALUES (?, FROM_UNIXTIME(?), 0)",
        linkId,
        at.getEpochSecond());
  }

  private static <T> T pick(Random random, List<T> values) {
    return values.get(random.nextInt(values.size()));
  }
}
