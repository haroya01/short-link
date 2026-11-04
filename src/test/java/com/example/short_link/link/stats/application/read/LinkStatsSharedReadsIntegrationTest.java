package com.example.short_link.link.stats.application.read;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.common.analytics.HeatmapCell;
import com.example.short_link.link.application.dto.LinkStats;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.stats.application.VisitorSketch;
import com.example.short_link.link.stats.domain.ClickEventEntity;
import com.example.short_link.link.stats.domain.ClickRollup;
import com.example.short_link.link.stats.domain.repository.ClickEventRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LinkStatsSharedReadsIntegrationTest {

  private static final Instant ROLLED_DAY = Instant.parse("2026-01-10T00:00:00Z");

  @Autowired private LinkStatsQueryService stats;
  @Autowired private UserRepository users;
  @Autowired private LinkRepository links;
  @Autowired private ClickEventRepository clicks;
  @Autowired private JdbcTemplate jdbc;
  @PersistenceContext private EntityManager em;

  private UserEntity owner;
  private LinkEntity link;
  private Instant at;

  @BeforeEach
  void aLinkCreatedTenDaysAgo() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    owner = users.save(new UserEntity(suffix + "@x.com", "google", "g-" + suffix));
    link =
        links.save(
            new LinkEntity("https://example.com/" + suffix, "s" + suffix, owner.getId(), null));
    em.flush();
    jdbc.update(
        "UPDATE link SET created_at = TIMESTAMPADD(SECOND, ?, '1970-01-01') WHERE id = ?",
        Instant.now().minus(Duration.ofDays(10)).getEpochSecond(),
        link.getId());
    em.refresh(link);
    at = Instant.now().minus(Duration.ofDays(2)).truncatedTo(ChronoUnit.HOURS);
    jdbc.update(
        "UPDATE click_rollup_state SET rolled_until = FROM_UNIXTIME(1),"
            + " aggregated_until = FROM_UNIXTIME(1) WHERE id = 1");
  }

  @Test
  void theFourFlagCountsKeepTheirOwnBotScopeAcrossRawClicksAndRollups() {
    click(human().referrer(null));
    click(human().referrer(null));
    click(bot().referrer(null));
    click(human().sourceChannel("profile-link"));
    click(human().sourceChannel("Profile-Card"));
    click(human().sourceChannel("post-7"));
    click(bot().sourceChannel("profile-link"));
    click(bot().botName("preview:slackbot"));
    click(human().botName("preview:relay"));
    click(bot().botName("datacenter:Amazon"));
    click(human().botName("datacenter:Relay"));
    click(bot().botName("crawler:googlebot"));
    rollupRow(ClickRollup.REFERRER, false, null, 5);
    rollupRow(ClickRollup.REFERRER, true, null, 7);
    rollupRow(ClickRollup.SOURCE_CHANNEL, false, "profile-bio", 3);
    rollupRow(ClickRollup.SOURCE_CHANNEL, true, "profile-bio", 4);
    rollupRow(ClickRollup.BOT_NAME, true, "preview:discordbot", 2);
    rollupRow(ClickRollup.BOT_NAME, false, "preview:relay", 6);
    rollupRow(ClickRollup.BOT_NAME, true, "datacenter:GCP", 8);
    rollupRow(ClickRollup.BOT_NAME, false, "datacenter:Relay", 9);

    LinkStats report = report();

    assertThat(report.channelClicks())
        .filteredOn(c -> c.channel().equals("direct"))
        .extracting(LinkStats.ChannelClick::count)
        .containsExactly(2L + 5L);
    assertThat(report.profileClicks()).isEqualTo(2L + 3L);
    assertThat(report.previewClicks()).isEqualTo(1L + 2L);
    assertThat(report.datacenterClicks()).isEqualTo(2L + 8L + 9L);
  }

  @Test
  void aVisitorReturnsOnlyAfterAGapOfThirtyMinutesOrMore() {
    visits("gap-29", 0, 29);
    visits("gap-30", 0, 30);
    visits("gap-31", 0, 31);
    visits("steps-of-10", 0, 10, 20, 30);
    visits("once", 0);
    click(bot().visitorHash("bot-visitor").clickedAt(at).referrerHost("a.example"));
    click(
        bot()
            .visitorHash("bot-visitor")
            .clickedAt(at.plus(Duration.ofHours(2)))
            .referrerHost("a.example"));
    click(human().visitorHash(null).clickedAt(at).referrerHost("a.example"));

    LinkStats report = report();

    assertThat(report.returnRate().newVisitors()).isEqualTo(3);
    assertThat(report.returnRate().returningVisitors()).isEqualTo(2);
    assertThat(report.channelDepth())
        .singleElement()
        .satisfies(
            depth -> {
              assertThat(depth.host()).isEqualTo("a.example");
              assertThat(depth.returningVisitors()).isEqualTo(2);
              assertThat(depth.returnRatio()).isEqualTo(0.4);
            });
  }

  @Test
  void hoursAndWeekdaysAreTheMarginsOfTheHeatmapInAHalfHourZone() {
    owner.changeTimezone("Asia/Kolkata");
    em.flush();
    Instant friday0015 = Instant.parse("2026-10-29T18:45:00Z");
    Instant friday2345 = Instant.parse("2026-10-30T18:15:00Z");
    Instant saturday0015 = Instant.parse("2026-10-30T18:45:00Z");
    jdbc.update(
        "UPDATE link SET created_at = TIMESTAMPADD(SECOND, ?, '1970-01-01') WHERE id = ?",
        Instant.parse("2026-10-01T00:00:00Z").getEpochSecond(),
        link.getId());
    em.refresh(link);
    for (Instant t : List.of(friday0015, friday2345, saturday0015)) {
      click(human().clickedAt(t));
    }
    click(bot().clickedAt(friday0015));

    LinkStats report = report();

    assertThat(report.heatmap())
        .containsExactly(
            new HeatmapCell("FRIDAY", 0, 1),
            new HeatmapCell("FRIDAY", 23, 1),
            new HeatmapCell("SATURDAY", 0, 1));
    assertThat(report.hourClicks())
        .containsExactly(new LinkStats.HourClick(0, 2), new LinkStats.HourClick(23, 1));
    assertThat(report.dayOfWeekClicks())
        .containsExactly(
            new LinkStats.DayOfWeekClick("FRIDAY", 2), new LinkStats.DayOfWeekClick("SATURDAY", 1));
    assertThat(report.peakHour()).isZero();
  }

  @Test
  void uniqueVisitorsCountTheRawHashesAloneUntilALinkHasRolledUpVisitors() {
    click(human().visitorHash("a"));
    click(human().visitorHash("a"));
    click(human().visitorHash("b"));
    click(bot().visitorHash("c"));

    assertThat(report().uniqueClicks()).isEqualTo(2);

    jdbc.update(
        "INSERT INTO click_daily_visitors (link_id, day_start, sketch) VALUES (?, FROM_UNIXTIME(?), ?)",
        link.getId(),
        ROLLED_DAY.getEpochSecond(),
        VisitorSketch.of(List.of("b", "d", "e")));

    assertThat(report().uniqueClicks()).isEqualTo(4);
  }

  private void visits(String visitor, int... minutes) {
    for (int minute : minutes) {
      click(
          human()
              .visitorHash(visitor)
              .clickedAt(at.plus(Duration.ofMinutes(minute)))
              .referrerHost("a.example"));
    }
  }

  private ClickEventEntity.ClickEventEntityBuilder human() {
    return ClickEventEntity.builder()
        .bot(false)
        .clickedAt(at)
        .referrer("https://news.example/story");
  }

  private ClickEventEntity.ClickEventEntityBuilder bot() {
    return human().bot(true);
  }

  private void click(ClickEventEntity.ClickEventEntityBuilder builder) {
    clicks.save(builder.linkId(link.linkId()).clientIp("1.1.1.1").build());
  }

  private void rollupRow(int dim, boolean bot, String value, int count) {
    jdbc.update(
        "INSERT INTO click_daily_dim "
            + "(link_id, dim, day_start, is_bot, value_key, value, num, clicks, first_at) "
            + "VALUES (?, ?, FROM_UNIXTIME(?), ?, UNHEX(MD5(?)), ?, NULL, ?, FROM_UNIXTIME(?))",
        link.getId(),
        dim,
        ROLLED_DAY.getEpochSecond(),
        bot,
        dim + "/" + bot + "/" + value,
        value,
        count,
        ROLLED_DAY.getEpochSecond());
  }

  private LinkStats report() {
    em.flush();
    return stats.stats(owner.getId(), link.getShortCode());
  }
}
