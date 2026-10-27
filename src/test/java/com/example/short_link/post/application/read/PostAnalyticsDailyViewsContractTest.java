package com.example.short_link.post.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

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
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PostAnalyticsDailyViewsContractTest {
  private static final Instant NOW = Instant.parse("2026-11-10T16:30:00Z");
  private static final Instant POST_CREATED = Instant.parse("2026-10-01T00:00:00Z");
  private static final List<Instant> EARLIER_VIEWS =
      List.of(
          Instant.parse("2026-10-14T02:00:00Z"),
          Instant.parse("2026-10-23T22:30:00Z"),
          Instant.parse("2026-10-27T15:30:00Z"),
          Instant.parse("2026-11-01T05:30:00Z"),
          Instant.parse("2026-11-04T16:00:00Z"),
          Instant.parse("2026-11-09T23:00:00Z"));

  @Autowired private UserRepository users;
  @Autowired private PostRepository posts;
  @Autowired private PostViewEventRepository views;
  @Autowired private PostAnalyticsQueryService analytics;
  @PersistenceContext private EntityManager em;
  @MockitoSpyBean private Clock clock;

  private UserEntity author;
  private PostEntity post;

  @BeforeEach
  void aPostViewedOverTheLastFourWeeks() {
    when(clock.instant()).thenReturn(NOW);
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    author = users.save(new UserEntity(suffix + "@x.com", "google", "g-" + suffix));
    post = posts.save(new PostEntity(author.getId(), "p-" + suffix, "Title", "ko"));
    em.flush();
    em.createNativeQuery("UPDATE posts SET created_at = :at WHERE id = :id")
        .setParameter("at", POST_CREATED)
        .setParameter("id", post.getId())
        .executeUpdate();
    em.refresh(post);
    EARLIER_VIEWS.forEach(at -> view(at, false));
  }

  @ParameterizedTest
  @CsvSource({
    "Asia/Seoul, 2026-11-04T16:00:00Z, false",
    "Asia/Seoul, 2026-11-10T16:00:00Z, false",
    "Asia/Seoul, 2026-11-05T14:59:59Z, false",
    "Europe/Berlin, 2026-10-23T22:30:00Z, true",
    "America/New_York, 2026-10-31T04:30:00Z, false",
  })
  void oneMoreViewRaisesOnlyItsLocalDayInTheAuthorsZone(String zone, Instant at, boolean bot) {
    author.changeTimezone(zone);
    LocalDate local = at.atZone(ZoneId.of(zone)).toLocalDate();
    Map<String, Map<LocalDate, Long>> before = series();

    view(at, bot);

    Map<String, Map<LocalDate, Long>> after = series();
    for (String name : before.keySet()) {
      assertThat(change(before.get(name), after.get(name)))
          .as(name)
          .containsExactly(Map.entry(local, 1L));
    }
    assertThat(analytics.postAnalytics(author.getId(), post.getId(), 30).windowViews())
        .isEqualTo(EARLIER_VIEWS.size() + 1);
  }

  private Map<String, Map<LocalDate, Long>> series() {
    return Map.of(
        "post 30 days", byDay(analytics.postAnalytics(author.getId(), post.getId(), 30).daily()),
        "post all time", byDay(analytics.postAnalytics(author.getId(), post.getId(), 0).daily()),
        "blog 30 days", byDay(analytics.overview(author.getId(), 30).daily()),
        "blog all time", byDay(analytics.overview(author.getId(), 0).daily()));
  }

  private void view(Instant at, boolean bot) {
    views.save(PostViewEventEntity.builder().postId(post.getId()).viewedAt(at).bot(bot).build());
    em.flush();
  }

  private static Map<LocalDate, Long> byDay(List<DailyPoint> daily) {
    return daily.stream().collect(Collectors.toMap(DailyPoint::date, DailyPoint::views));
  }

  private static <K> Map<K, Long> change(Map<K, Long> before, Map<K, Long> after) {
    Set<K> cells = new HashSet<>(before.keySet());
    cells.addAll(after.keySet());
    Map<K, Long> change = new TreeMap<>();
    for (K cell : cells) {
      long diff = after.getOrDefault(cell, 0L) - before.getOrDefault(cell, 0L);
      if (diff != 0) change.put(cell, diff);
    }
    return change;
  }
}
