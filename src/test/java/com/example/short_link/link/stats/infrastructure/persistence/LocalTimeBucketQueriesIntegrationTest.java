package com.example.short_link.link.stats.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.admin.domain.repository.AdminAnalyticsRepository.ActivePerDayRow;
import com.example.short_link.admin.domain.repository.AdminMetricsRepository.DailyRow;
import com.example.short_link.admin.infrastructure.persistence.JpaAdminAnalyticsRepository;
import com.example.short_link.admin.infrastructure.persistence.JpaAdminMetricsRepository;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.stats.domain.ClickEventEntity;
import com.example.short_link.link.stats.domain.repository.ClickEventRepository;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.HeatmapRow;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LocalTimeBucketQueriesIntegrationTest {
  private static final String SEOUL = "+09:00";
  private static final Instant SEOUL_EVENING = Instant.parse("2026-11-06T11:00:00Z");
  private static final LocalDate SEOUL_DAY = LocalDate.parse("2026-11-06");
  private static final int FRIDAY = 6;
  private static final int EVENING = 20;
  private static final Instant SINCE = Instant.parse("2026-11-01T00:00:00Z");

  @Autowired private UserRepository users;
  @Autowired private LinkRepository links;
  @Autowired private ClickEventRepository clicks;
  @Autowired private JpaClickRangeReadRepository range;
  @Autowired private JpaClickAlertReadRepository alerts;
  @Autowired private JpaAdminMetricsRepository adminMetrics;
  @Autowired private JpaAdminAnalyticsRepository adminAnalytics;
  @PersistenceContext private EntityManager em;

  private UserEntity owner;
  private LinkEntity link;

  @BeforeEach
  void theDatabaseSessionIsNotUtc() {
    Number sessionOffsetHours =
        (Number)
            em.createNativeQuery("SELECT TIMESTAMPDIFF(HOUR, UTC_TIMESTAMP(), NOW())")
                .getSingleResult();
    assertThat(sessionOffsetHours.intValue()).isNotZero();
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    owner = users.save(new UserEntity(suffix + "@x.com", "google", "g-" + suffix));
    link =
        links.save(
            new LinkEntity("https://example.com/" + suffix, "t" + suffix, owner.getId(), null));
    em.flush();
  }

  @Test
  void theWeeklyHeatmapPutsAClickInItsLocalDayAndHour() {
    click(SEOUL_EVENING);

    List<HeatmapRow> rows =
        range.findHeatmapByUserIdAndRange(
            owner.getId(), SINCE, SINCE.plusSeconds(864_000), SEOUL, PageRequest.ofSize(10));

    assertThat(rows)
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.getDow()).isEqualTo(FRIDAY);
              assertThat(row.getHour()).isEqualTo(EVENING);
            });
  }

  @Test
  void theClickAlertPeakHourIsTheLocalHour() {
    click(SEOUL_EVENING);

    assertThat(
            alerts.findPeakHourByLinkIdAndRange(
                link.getId(), SINCE, SINCE.plusSeconds(864_000), SEOUL))
        .hasValueSatisfying(row -> assertThat(row.getHour()).isEqualTo(EVENING));
  }

  @Test
  void adminDailyClicksAndActiveUsersCountAClickOnItsLocalDay() {
    Map<LocalDate, Long> clicksBefore =
        days(() -> adminMetrics.dailyClicksSince(SINCE, SEOUL, -1L));
    Map<LocalDate, Long> activeBefore = active();

    click(SEOUL_EVENING);

    assertThat(change(clicksBefore, days(() -> adminMetrics.dailyClicksSince(SINCE, SEOUL, -1L))))
        .containsExactly(Map.entry(SEOUL_DAY, 1L));
    assertThat(change(activeBefore, active())).containsExactly(Map.entry(SEOUL_DAY, 1L));
  }

  @Test
  void adminDailySignupsAndLinksCountACreationOnItsLocalDay() {
    Map<LocalDate, Long> signupsBefore = days(() -> adminMetrics.dailySignupsSince(SINCE, SEOUL));
    Map<LocalDate, Long> linksBefore = days(() -> adminMetrics.dailyLinksSince(SINCE, SEOUL));

    createdAt("users", owner.getId());
    createdAt("link", link.getId());

    assertThat(change(signupsBefore, days(() -> adminMetrics.dailySignupsSince(SINCE, SEOUL))))
        .containsExactly(Map.entry(SEOUL_DAY, 1L));
    assertThat(change(linksBefore, days(() -> adminMetrics.dailyLinksSince(SINCE, SEOUL))))
        .containsExactly(Map.entry(SEOUL_DAY, 1L));
  }

  private void click(Instant at) {
    clicks.save(ClickEventEntity.builder().linkId(link.linkId()).clickedAt(at).build());
    em.flush();
  }

  private void createdAt(String table, Long id) {
    em.createNativeQuery(
            "UPDATE " + table + " SET created_at = FROM_UNIXTIME(:epoch) WHERE id = :id")
        .setParameter("epoch", SEOUL_EVENING.getEpochSecond())
        .setParameter("id", id)
        .executeUpdate();
  }

  private Map<LocalDate, Long> active() {
    return adminAnalytics.dailyActiveUsers(SINCE, SEOUL).stream()
        .collect(Collectors.toMap(ActivePerDayRow::getBucket, ActivePerDayRow::getActive));
  }

  private static Map<LocalDate, Long> days(Supplier<List<DailyRow>> query) {
    return query.get().stream().collect(Collectors.toMap(DailyRow::getDay, DailyRow::getCount));
  }

  private static Map<LocalDate, Long> change(
      Map<LocalDate, Long> before, Map<LocalDate, Long> after) {
    return after.entrySet().stream()
        .filter(e -> !e.getValue().equals(before.getOrDefault(e.getKey(), 0L)))
        .collect(
            Collectors.toMap(
                Map.Entry::getKey, e -> e.getValue() - before.getOrDefault(e.getKey(), 0L)));
  }
}
