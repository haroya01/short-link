package com.example.short_link.post.application.read;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostViewEventEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.PostViewEventRepository;
import com.example.short_link.support.DiscoverableBodies;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

// Trending must rank by recent-window views, not all-time view_count — the whole point of the view
// event log. Only the real LEFT JOIN + windowed COUNT against MySQL proves it, so this drives the
// feed end-to-end through the service.
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PublicFeedTrendingIntegrationTest {

  @Autowired private PublicFeedQueryService service;
  @Autowired private PostRepository postRepository;
  @Autowired private PostViewEventRepository postViewEventRepository;
  @Autowired private UserRepository userRepository;

  private long author(String handle) {
    UserEntity u = userRepository.save(new UserEntity(handle + "@x.com", "google", "g-" + handle));
    u.claimUsername(handle);
    userRepository.save(u);
    return u.getId();
  }

  private long publish(long userId, String slug, int lifetimeViews) {
    return publish(userId, slug, lifetimeViews, "ko");
  }

  private long publish(long userId, String slug, int lifetimeViews, String lang) {
    PostEntity p = new PostEntity(userId, slug, slug, lang);
    for (int i = 0; i < lifetimeViews; i++) p.incrementViewCount();
    DiscoverableBodies.discoverable(p);
    p.publish();
    return postRepository.save(p).getId();
  }

  private void view(long postId, Instant at) {
    postViewEventRepository.save(new PostViewEventEntity(postId, at));
  }

  private List<String> trendingSlugs() {
    return service.feed(PublicFeedQuery.from(null, null, "trending", null, 0, 50)).items().stream()
        .map(PublicFeedItem::slug)
        .toList();
  }

  @Test
  void ranksByRecentWindowViewsNotLifetimeCount() {
    long a = author("trendauthor");
    Instant now = Instant.now();
    Instant inWindow = now.minus(1, ChronoUnit.HOURS);
    Instant outOfWindow = now.minus(8, ChronoUnit.DAYS);

    // High lifetime counter but every view is old (outside the 7-day window) → must sink despite a
    // big view_count. This is the dishonest case the old "ORDER BY view_count" surfaced on top.
    long stale = publish(a, "trend-stale-star", 500);
    for (int i = 0; i < 5; i++) view(stale, outOfWindow);

    long fresh = publish(a, "trend-fresh-buzz", 3);
    for (int i = 0; i < 5; i++) view(fresh, inWindow);

    long mild = publish(a, "trend-mild-warm", 0);
    for (int i = 0; i < 2; i++) view(mild, inWindow);

    List<String> slugs = trendingSlugs();
    assertThat(slugs).contains("trend-fresh-buzz", "trend-mild-warm", "trend-stale-star");
    assertThat(slugs.indexOf("trend-fresh-buzz")).isLessThan(slugs.indexOf("trend-mild-warm"));
    assertThat(slugs.indexOf("trend-mild-warm")).isLessThan(slugs.indexOf("trend-stale-star"));
  }

  @Test
  void postsWithoutRecentViewsStillAppearByRecency() {
    long a = author("fallbackauthor");
    // No view events at all — windowed trending must still include it (LEFT JOIN fallback), not
    // drop
    // posts that simply have no recent traction yet.
    publish(a, "trend-quiet-newcomer", 0);

    assertThat(trendingSlugs()).contains("trend-quiet-newcomer");
  }

  @Test
  void languageFilterNarrowsTrendingToOnePostLanguage() {
    long a = author("langauthor");
    publish(a, "trend-lang-ko", 0, "ko");
    publish(a, "trend-lang-ja", 0, "ja");

    List<String> ja =
        service.feed(PublicFeedQuery.from(null, null, "trending", "ja", 0, 50)).items().stream()
            .map(PublicFeedItem::slug)
            .toList();
    assertThat(ja).contains("trend-lang-ja").doesNotContain("trend-lang-ko");

    List<String> all = trendingSlugs();
    assertThat(all).contains("trend-lang-ko", "trend-lang-ja");
  }
}
