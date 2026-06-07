package com.example.short_link.post.application.read;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostViewEventEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.PostViewEventRepository;
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

/**
 * Exercises the search JPQL against a real DB — the LIKE pattern, the tag join, the author-handle
 * subquery and the wildcard escaping only show up at query time, so a mock unit test can't catch a
 * malformed clause.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PublicFeedSearchIntegrationTest {

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

  private void publish(long userId, String slug, String title, String excerpt, List<String> tags) {
    PostEntity p = new PostEntity(userId, slug, title, "ko");
    p.updateExcerpt(excerpt);
    p.updateTags(tags);
    p.publish();
    postRepository.save(p);
  }

  private List<String> slugs(String query) {
    return service.search(query, "recent", null, 0, 20).items().stream()
        .map(PublicFeedItem::slug)
        .toList();
  }

  @Test
  void matchesTitleExcerptTagAndAuthorHandle() {
    long alice = author("alice");
    long bob = author("bob");
    publish(
        alice,
        "hexagonal",
        "Hexagonal architecture in Spring",
        "ports and adapters",
        List.of("spring"));
    publish(bob, "react-hooks", "Learning React", "all about hooks", List.of("react", "frontend"));
    publish(alice, "k8s", "Deploying to Kubernetes", "rollout strategies", List.of("devops"));

    assertThat(slugs("hexagonal")).containsExactly("hexagonal"); // title
    assertThat(slugs("hooks")).containsExactly("react-hooks"); // excerpt
    assertThat(slugs("frontend")).containsExactly("react-hooks"); // tag
    assertThat(slugs("alice")).containsExactlyInAnyOrder("hexagonal", "k8s"); // author handle
  }

  @Test
  void isCaseInsensitive() {
    long alice = author("alice");
    publish(alice, "spring", "Spring Boot Internals", "the refresh cycle", List.of("spring"));

    assertThat(slugs("SPRING")).containsExactly("spring");
    assertThat(slugs("internals")).containsExactly("spring");
  }

  @Test
  void excludesDrafts() {
    long alice = author("alice");
    PostEntity draft = new PostEntity(alice, "draft", "Draft about Kafka", "ko");
    draft.updateExcerpt("wip");
    draft.updateTags(List.of("kafka"));
    postRepository.save(draft); // never published

    assertThat(slugs("kafka")).isEmpty();
  }

  @Test
  void emptyForNoMatch() {
    author("alice");
    publish(author("bob"), "a", "Something", "anything", List.of("tag"));

    assertThat(slugs("nonexistentterm")).isEmpty();
  }

  @Test
  void escapesLikeWildcards() {
    long alice = author("alice");
    publish(alice, "plain", "Plain title", "plain body", List.of("plain"));

    // A bare '%' must be a literal, not "match everything".
    assertThat(slugs("%")).isEmpty();
    assertThat(slugs("_")).isEmpty();
  }

  @Test
  void suggestedAuthorsRankByPublishedPostCount() {
    long alice = author("alice");
    long bob = author("bob");
    publish(alice, "a1", "One", "x", List.of());
    publish(alice, "a2", "Two", "x", List.of());
    publish(bob, "b1", "Three", "x", List.of());

    List<SuggestedAuthorView> suggested = service.suggestedAuthors(5);

    assertThat(suggested).extracting(s -> s.author().username()).containsExactly("alice", "bob");
    assertThat(suggested.get(0).postCount()).isEqualTo(2);
  }

  @Test
  void suggestedAuthorsExcludeAuthorsWithOnlyDrafts() {
    long alice = author("alice");
    PostEntity draft = new PostEntity(alice, "d", "Draft", "ko");
    postRepository.save(draft); // never published

    assertThat(service.suggestedAuthors(5)).isEmpty();
  }

  private long publishReturningId(
      long userId, String slug, String title, List<String> tags, int lifetimeViews) {
    PostEntity p = new PostEntity(userId, slug, title, "ko");
    p.updateTags(tags);
    for (int i = 0; i < lifetimeViews; i++) p.incrementViewCount();
    p.publish();
    return postRepository.save(p).getId();
  }

  private void view(long postId, Instant at) {
    postViewEventRepository.save(new PostViewEventEntity(postId, at));
  }

  private List<String> trendingSlugs(String query) {
    return service.search(query, "trending", null, 0, 20).items().stream()
        .map(PublicFeedItem::slug)
        .toList();
  }

  @Test
  void trendingSearchRanksByRecentWindowViewsNotLifetimeCount() {
    long a = author("searchtrend");
    Instant now = Instant.now();
    Instant inWindow = now.minus(2, ChronoUnit.HOURS);
    Instant outOfWindow = now.minus(10, ChronoUnit.DAYS);

    // All three match "kotlin" by title; the trending sort must follow recent-window views, not the
    // lifetime view_count — the same honesty fix the main feed got, now inside search.
    long stale = publishReturningId(a, "kotlin-stale", "Kotlin deep dive", List.of(), 800);
    for (int i = 0; i < 6; i++) view(stale, outOfWindow); // big lifetime count, but all old

    long fresh = publishReturningId(a, "kotlin-fresh", "Kotlin coroutines", List.of(), 1);
    for (int i = 0; i < 4; i++) view(fresh, inWindow);

    long mild = publishReturningId(a, "kotlin-mild", "Kotlin DSLs", List.of(), 0);
    view(mild, inWindow);

    assertThat(trendingSlugs("kotlin"))
        .containsExactly("kotlin-fresh", "kotlin-mild", "kotlin-stale");
  }
}
