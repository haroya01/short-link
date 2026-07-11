package com.example.short_link.post.application.read;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.post.application.write.PostSearchTextFlattener;
import com.example.short_link.post.domain.PostBlockEntity;
import com.example.short_link.post.domain.PostBlockType;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostViewEventEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.PostViewEventRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

/**
 * Exercises the FULLTEXT(ngram) search against a real MySQL — the MATCH() clause, the author-handle
 * subquery, relevance ranking and Korean partial matching only show up at query time, so a mock
 * unit test can't catch a malformed clause. search_text is populated the way the write path does it
 * (via {@link PostSearchTextFlattener}) so these posts mirror production rows.
 *
 * <p>★ NOT @Transactional (unlike most integration tests here). InnoDB FULLTEXT is invisible to a
 * MATCH() that runs inside the SAME transaction as the INSERT — the just-inserted document sits in
 * an FTS cache that only becomes searchable on commit. A rollback-per-test model would make every
 * MATCH find nothing. So each write commits (Spring Data save() is its own tx), and {@link
 * #cleanUp} truncates the tables afterward.
 */
@SpringBootTest
@ActiveProfiles("test")
class PublicFeedSearchIntegrationTest {

  @Autowired private PublicFeedQueryService service;
  @Autowired private PostRepository postRepository;
  @Autowired private PostViewEventRepository postViewEventRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private JdbcTemplate jdbc;

  private final PostSearchTextFlattener flattener =
      new PostSearchTextFlattener(JsonMapper.builder().build());

  @AfterEach
  void cleanUp() {
    // Committed rows must be cleared between tests (no rollback). Child tables first for the FKs.
    jdbc.update("DELETE FROM post_view_event");
    jdbc.update("DELETE FROM post_block");
    jdbc.update("DELETE FROM post_tag");
    jdbc.update("DELETE FROM posts");
    jdbc.update("DELETE FROM users");
  }

  private long author(String handle) {
    UserEntity u = userRepository.save(new UserEntity(handle + "@x.com", "google", "g-" + handle));
    u.claimUsername(handle);
    userRepository.save(u);
    return u.getId();
  }

  private void publish(long userId, String slug, String title, String excerpt, List<String> tags) {
    publish(userId, slug, title, excerpt, tags, List.of());
  }

  // Publish with body blocks — persists post_block rows AND the derived search_text, exactly as the
  // write use cases do, so MATCH(search_text) reaches title + excerpt + tags + body.
  private void publish(
      long userId,
      String slug,
      String title,
      String excerpt,
      List<String> tags,
      List<PostBlockEntity> blocks) {
    PostEntity p = new PostEntity(userId, slug, title, "ko");
    p.updateExcerpt(excerpt);
    p.updateTags(tags);
    p.updateSearchText(flattener.flatten(title, excerpt, tags, blocks));
    p.publish();
    postRepository.save(p);
  }

  private static PostBlockEntity paragraph(String text) {
    return new PostBlockEntity(null, PostBlockType.PARAGRAPH, text, 0);
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
    p.updateSearchText(flattener.flatten(title, null, tags, List.of()));
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

  private List<String> relevanceSlugs(String query) {
    return service.search(query, "relevance", null, 0, 20).items().stream()
        .map(PublicFeedItem::slug)
        .toList();
  }

  // H1 회귀 방지의 핵심: 예전 검색은 본문(post_block)을 통째로 놓쳤다. 이제 본문 단어로 글이 잡혀야 한다.
  @Test
  void matchesBodyBlockText() {
    long a = author("writer");
    publish(
        a,
        "with-body",
        "무난한 제목",
        "짧은 요약",
        List.of("일반"),
        List.of(paragraph("본문에만 등장하는 리다이렉트 성능 최적화 이야기"), paragraph("두 번째 문단은 캐시 전략을 다룬다")));
    // 제목·요약·태그 어디에도 없고 오직 본문에만 있는 단어.
    assertThat(relevanceSlugs("리다이렉트")).containsExactly("with-body");
    assertThat(relevanceSlugs("캐시")).containsExactly("with-body");
  }

  // H2 관련성 랭킹: 검색어가 더 많이/무겁게 겹치는 글이 위로.
  @Test
  void relevanceRanksStrongerMatchFirst() {
    long a = author("ranker");
    publish(
        a,
        "strong",
        "스프링 성능 튜닝 총정리",
        "스프링 성능",
        List.of("스프링", "성능"),
        List.of(paragraph("스프링 성능 병목을 성능 프로파일링으로 잡는다")));
    publish(a, "weak", "잡담 모음", "이것저것", List.of("일상"), List.of(paragraph("어쩌다 성능 이야기를 한 줄 스쳤다")));

    List<String> ranked = relevanceSlugs("스프링 성능");
    assertThat(ranked).containsExactly("strong", "weak");
  }

  // H3 한글 부분일치(ngram token size 2): 두 글자 부분어로도 잡혀야 한다.
  @Test
  void koreanPartialMatch() {
    long a = author("kwriter");
    publish(
        a, "korean", "데이터베이스 인덱스 설계", null, List.of(), List.of(paragraph("인덱스 선택도와 카디널리티에 대하여")));
    // "인덱스"의 부분(두 글자 이상)으로도 매칭.
    assertThat(relevanceSlugs("인덱")).containsExactly("korean");
    assertThat(relevanceSlugs("데이터")).containsExactly("korean");
  }

  // 하위호환: sort 미지정(기본=relevance)에서도 예전 title/tag/author 매칭이 그대로 잡힌다.
  @Test
  void defaultRelevanceSortStillMatchesTitleTagAndAuthor() {
    long alice = author("aliceR");
    long bob = author("bobR");
    publish(alice, "r-hex", "Hexagonal in Spring", "ports", List.of("spring"));
    publish(bob, "r-react", "Learning React", "hooks", List.of("frontend"));

    assertThat(relevanceSlugs("hexagonal")).containsExactly("r-hex"); // title
    assertThat(relevanceSlugs("frontend")).containsExactly("r-react"); // tag
    assertThat(relevanceSlugs("aliceR")).containsExactly("r-hex"); // author handle
  }
}
