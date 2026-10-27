package com.example.short_link.profile.application.visit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.short_link.common.analytics.HeatmapCell;
import com.example.short_link.common.analytics.VisitStats;
import com.example.short_link.profile.domain.repository.ProfileVisitRepository;
import com.example.short_link.profile.domain.visit.ProfileVisitEntity;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProfileStatsDaylightSavingTest {

  private static final Instant NOW = Instant.parse("2026-11-10T15:00:00Z");
  private static final Instant HALF_PAST_MIDNIGHT_BEFORE_FALL_BACK =
      Instant.parse("2026-10-31T04:30:00Z");
  private static final Instant NINE_AFTER_FALL_BACK = Instant.parse("2026-11-05T14:00:00Z");
  private static final DateTimeFormatter UTC_WALL_CLOCK =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

  @Autowired private UserRepository users;
  @Autowired private ProfileVisitRepository visits;
  @Autowired private ProfileStatsService stats;
  @PersistenceContext private EntityManager em;
  @MockitoBean private Clock clock;

  private UserEntity owner;

  @BeforeEach
  void aProfileVisitedOnBothSidesOfNewYorksFallBack() {
    when(clock.instant()).thenReturn(NOW);
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    owner = users.save(new UserEntity(suffix + "@x.com", "google", "g-" + suffix));
    visit(HALF_PAST_MIDNIGHT_BEFORE_FALL_BACK);
    visit(NINE_AFTER_FALL_BACK);
  }

  @Test
  void eachVisitKeepsTheLocalTimeItHadInNewYorkWhenItHappened() {
    owner.changeTimezone("America/New_York");

    VisitStats stats = this.stats.statsForOwner(owner.getId());

    assertThat(stats.dailyVisits())
        .containsExactly(
            new VisitStats.DailyVisit(LocalDate.of(2026, 10, 31), 1),
            new VisitStats.DailyVisit(LocalDate.of(2026, 11, 5), 1));
    assertThat(stats.hourVisits())
        .containsExactly(new VisitStats.HourVisit(0, 1), new VisitStats.HourVisit(9, 1));
    assertThat(stats.heatmap())
        .containsExactly(new HeatmapCell("THURSDAY", 9, 1), new HeatmapCell("SATURDAY", 0, 1));
  }

  @Test
  void aZoneWithoutDaylightSavingBucketsAsBefore() {
    owner.changeTimezone("Asia/Seoul");

    VisitStats stats = this.stats.statsForOwner(owner.getId());

    assertThat(stats.hourVisits())
        .containsExactly(new VisitStats.HourVisit(13, 1), new VisitStats.HourVisit(23, 1));
    assertThat(stats.heatmap())
        .containsExactly(new HeatmapCell("THURSDAY", 23, 1), new HeatmapCell("SATURDAY", 13, 1));
  }

  private void visit(Instant at) {
    ProfileVisitEntity visit =
        visits.save(ProfileVisitEntity.builder().profileUserId(owner.getId()).bot(false).build());
    em.flush();
    em.createNativeQuery("UPDATE profile_visit_event SET visited_at = ?1 WHERE id = ?2")
        .setParameter(1, UTC_WALL_CLOCK.format(at))
        .setParameter(2, visit.getId())
        .executeUpdate();
  }
}
