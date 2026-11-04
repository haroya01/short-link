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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@SpringBootTest
@ActiveProfiles("test")
class LinkStatsReportGoldenIntegrationTest {

  private static final Instant NOW = Instant.parse("2026-10-20T06:30:00Z");
  private static final Path GOLDEN = Path.of("src/test/resources/link-stats-golden/reports.json");
  private static final List<String> ZONES =
      List.of("Asia/Seoul", "America/New_York", "Asia/Kolkata", "Asia/Kathmandu");
  private static final JsonMapper PRETTY =
      JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

  @Autowired private LinkStatsQueryService stats;
  @Autowired private UserRepository users;
  @Autowired private LinkRepository links;
  @Autowired private ClickRollupService rollup;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private JsonMapper json;
  @MockitoSpyBean private Clock clock;

  private final List<Long> userIds = new ArrayList<>();
  private final List<Long> linkIds = new ArrayList<>();

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
  void aReportReadsTheSameForEveryZoneAcrossRawAndRolledUpDays() throws IOException {
    Map<String, ShortCode> codes = new LinkedHashMap<>();
    Map<String, Long> owners = new LinkedHashMap<>();
    Instant created = NOW.minus(Duration.ofDays(230)).minusSeconds(4_321);
    for (int i = 0; i < ZONES.size(); i++) {
      String zone = ZONES.get(i);
      Long owner = owner(zone);
      LinkEntity link = link(owner, created);
      owners.put(zone, owner);
      codes.put(zone, link.getShortCode());
      seed(link.getId(), ZoneId.of(zone), created, new Random(20261020L + i));
    }
    rollup.rollUp(NOW);
    rollup.deleteRolledUp();

    ObjectNode reports = PRETTY.createObjectNode();
    for (String zone : ZONES) {
      JsonNode report = json.valueToTree(stats.stats(owners.get(zone), codes.get(zone)));
      ((ObjectNode) report).remove(List.of("shortCode", "velocity"));
      reports.set(zone, canonical(report));
    }

    assertThat(PRETTY.writeValueAsString(reports) + "\n")
        .isEqualTo(Files.readString(GOLDEN, StandardCharsets.UTF_8));
  }

  private static JsonNode canonical(JsonNode node) {
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
      ArrayNode out = PRETTY.createArrayNode();
      sorted.forEach(out::add);
      return out;
    }
    return node;
  }

  private static boolean sameCount(JsonNode a, JsonNode b) {
    return a.has("count") && b.has("count") && a.get("count").equals(b.get("count"));
  }

  private void seed(Long linkId, ZoneId zone, Instant created, Random random) {
    long span = Duration.between(created, NOW).minusHours(1).getSeconds();
    List<Instant> times = new ArrayList<>();
    for (int i = 0; i < 320; i++) {
      times.add(created.plusSeconds((long) (random.nextDouble() * span)));
    }
    for (int daysAgo : new int[] {150, 91, 60, 3}) {
      LocalDate day = NOW.minus(Duration.ofDays(daysAgo)).atZone(zone).toLocalDate();
      Instant localMidnight = day.atStartOfDay(zone).toInstant();
      Instant utcMidnight = day.atStartOfDay(ZoneOffset.UTC).toInstant();
      times.addAll(
          List.of(
              localMidnight.minusSeconds(1),
              localMidnight,
              utcMidnight.minusSeconds(1),
              utcMidnight));
    }
    List<Object[]> rows = new ArrayList<>();
    for (Instant at : times) {
      rows.add(row(linkId, at, random, pick(random, VISITORS)));
    }
    Instant sessions = NOW.minus(Duration.ofDays(5));
    int[][] gaps = {{0, 29}, {0, 30}, {0, 31}, {0, 45}, {0, 10, 20, 30}};
    for (int v = 0; v < gaps.length; v++) {
      for (int minute : gaps[v]) {
        rows.add(
            row(linkId, sessions.plus(Duration.ofMinutes(minute)), random, "gap-" + v + "@" + v));
      }
    }
    jdbc.batchUpdate(
        "INSERT INTO click_event (link_id, clicked_at, is_bot, bot_name, referrer, referrer_host,"
            + " device_class, os_name, browser_name, utm_campaign, utm_source, utm_medium,"
            + " utm_content, utm_term, client_app, fetch_site, source_channel, country_code,"
            + " region_name, region_code, city_name, language, visitor_hash, asn, asn_org,"
            + " client_ip) VALUES (?, FROM_UNIXTIME(?), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,"
            + " ?, ?, ?, ?, ?, ?, ?, ?, ?, '10.0.0.0')",
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
      pick(random, Arrays.asList(null, "iOS", "Android", "Windows", "macOS")),
      pick(random, Arrays.asList(null, "Safari", "Chrome", "Samsung Internet")),
      pick(random, Arrays.asList(null, null, "newsletter", "launch")),
      pick(random, Arrays.asList(null, null, "twitter", "kakao", "Kakao")),
      pick(random, Arrays.asList(null, null, "social")),
      pick(random, Arrays.asList(null, null, "hero")),
      pick(random, Arrays.asList(null, null, "spring")),
      pick(random, Arrays.asList(null, null, "kakaotalk", "instagram")),
      pick(random, Arrays.asList(null, "cross-site", "none", "same-origin")),
      pick(random, Arrays.asList(null, null, "profile-links", "Profile-card", "qr")),
      region[0],
      region[1],
      region[2],
      pick(random, Arrays.asList(null, "Gangnam", "Shibuya", "Brooklyn")),
      pick(random, Arrays.asList(null, "ko", "ja", "en")),
      bot ? null : visitor,
      asn[0] == null ? null : Integer.valueOf(asn[0]),
      asn[1],
    };
  }

  private static final List<String> REFERRERS =
      Arrays.asList(
          null,
          null,
          "https://news.ycombinator.com/item",
          "https://www.google.com/",
          "https://x.com/someone/status/1",
          "https://l.instagram.com/",
          "https://blog.example.org/post/2",
          "https://Blog.Example.org/post/2");

  private static final List<String[]> REGIONS =
      List.of(
          new String[] {null, null, null},
          new String[] {"KR", "Seoul", "KR-11"},
          new String[] {"KR", "Seoul", null},
          new String[] {"JP", "Tokyo", "JP-13"},
          new String[] {"US", "New York", "US-NY"},
          new String[] {"IN", "Karnataka", "IN-KA"});

  private static final List<String[]> ASNS =
      List.of(
          new String[] {null, null},
          new String[] {"4766", "Korea Telecom"},
          new String[] {"16509", "AMAZON-02"},
          new String[] {"13335", "CLOUDFLARENET"});

  private static final List<String> BOT_NAMES =
      Arrays.asList(
          null,
          "preview:slackbot",
          "preview:kakaotalk-scrap",
          "Preview:Discordbot",
          "datacenter:AWS",
          "crawler:googlebot",
          "rate-flood");

  private static final List<String> HUMAN_BOT_NAMES =
      Arrays.asList(null, null, null, null, null, null, null, "datacenter:Relay");

  private static final List<String> VISITORS = visitors();

  private static List<String> visitors() {
    List<String> pool = new ArrayList<>();
    for (int i = 0; i < 70; i++) pool.add("visitor-" + i);
    for (int i = 0; i < 20; i++) pool.add(null);
    return pool;
  }

  private static <T> T pick(Random random, List<T> values) {
    return values.get(random.nextInt(values.size()));
  }

  private Long owner(String zone) {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    UserEntity user = new UserEntity("golden-" + suffix + "@x.com", "google", "g-g" + suffix);
    user.changeTimezone(zone);
    Long id = users.save(user).getId();
    userIds.add(id);
    return id;
  }

  private LinkEntity link(Long owner, Instant created) {
    String code = "g" + UUID.randomUUID().toString().substring(0, 7);
    LinkEntity link = links.save(new LinkEntity("https://example.com/" + code, code, owner, null));
    jdbc.update(
        "UPDATE link SET created_at = FROM_UNIXTIME(?) WHERE id = ?",
        created.getEpochSecond(),
        link.getId());
    linkIds.add(link.getId());
    return link;
  }

  private void clearRollups() {
    jdbc.update("DELETE FROM click_hourly");
    jdbc.update("DELETE FROM click_daily_dim");
    jdbc.update("DELETE FROM click_daily_visitors");
    jdbc.update("UPDATE click_rollup_state SET rolled_until = FROM_UNIXTIME(1) WHERE id = 1");
  }
}
