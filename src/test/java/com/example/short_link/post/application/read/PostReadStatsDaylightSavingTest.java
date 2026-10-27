package com.example.short_link.post.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.short_link.common.analytics.HeatmapCell;
import com.example.short_link.common.analytics.VisitStats;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostViewEventEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.PostViewEventRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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
class PostReadStatsDaylightSavingTest {

  private static final Instant NOW = Instant.parse("2026-11-10T15:00:00Z");
  private static final Instant HALF_PAST_MIDNIGHT_BEFORE_FALL_BACK =
      Instant.parse("2026-10-31T04:30:00Z");
  private static final Instant NINE_AFTER_FALL_BACK = Instant.parse("2026-11-05T14:00:00Z");

  @Autowired private UserRepository users;
  @Autowired private PostRepository posts;
  @Autowired private PostViewEventRepository views;
  @Autowired private PostReadStatsService stats;
  @PersistenceContext private EntityManager em;
  @MockitoBean private Clock clock;

  private UserEntity author;
  private PostEntity post;

  @BeforeEach
  void aPostReadOnBothSidesOfNewYorksFallBack() {
    when(clock.instant()).thenReturn(NOW);
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    author = users.save(new UserEntity(suffix + "@x.com", "google", "g-" + suffix));
    post = posts.save(new PostEntity(author.getId(), "p-" + suffix, "P", "ko"));
    views.save(new PostViewEventEntity(post.getId(), HALF_PAST_MIDNIGHT_BEFORE_FALL_BACK));
    views.save(new PostViewEventEntity(post.getId(), NINE_AFTER_FALL_BACK));
    em.flush();
  }

  @Test
  void eachViewKeepsTheLocalTimeItHadInNewYorkWhenItHappened() {
    author.changeTimezone("America/New_York");

    VisitStats stats = this.stats.forPost(author.getId(), post.getId());

    assertThat(stats.dailyVisits())
        .containsExactly(
            new VisitStats.DailyVisit(LocalDate.of(2026, 10, 31), 1),
            new VisitStats.DailyVisit(LocalDate.of(2026, 11, 5), 1));
    assertThat(stats.hourVisits())
        .containsExactly(new VisitStats.HourVisit(0, 1), new VisitStats.HourVisit(9, 1));
    assertThat(stats.heatmap())
        .containsExactly(new HeatmapCell("THURSDAY", 9, 1), new HeatmapCell("SATURDAY", 0, 1));
    assertThat(stats.peakHour()).isZero();
  }

  @Test
  void aZoneWithoutDaylightSavingBucketsAsBefore() {
    author.changeTimezone("Asia/Seoul");

    VisitStats stats = this.stats.forPost(author.getId(), post.getId());

    assertThat(stats.dailyVisits())
        .containsExactly(
            new VisitStats.DailyVisit(LocalDate.of(2026, 10, 31), 1),
            new VisitStats.DailyVisit(LocalDate.of(2026, 11, 5), 1));
    assertThat(stats.hourVisits())
        .containsExactly(new VisitStats.HourVisit(13, 1), new VisitStats.HourVisit(23, 1));
    assertThat(stats.heatmap())
        .containsExactly(new HeatmapCell("THURSDAY", 23, 1), new HeatmapCell("SATURDAY", 13, 1));
  }
}
