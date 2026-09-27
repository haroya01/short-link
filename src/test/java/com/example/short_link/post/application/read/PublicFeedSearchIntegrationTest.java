package com.example.short_link.post.application.read;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.post.application.write.CreatePostCommand;
import com.example.short_link.post.application.write.CreatePostUseCase;
import com.example.short_link.post.application.write.PostSearchTextFlattener;
import com.example.short_link.post.application.write.PublishPostCommand;
import com.example.short_link.post.application.write.PublishPostUseCase;
import com.example.short_link.post.domain.PostBlockEntity;
import com.example.short_link.post.domain.PostBlockType;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostViewEventEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.PostSearchTextRepository;
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

// Exercises the FULLTEXT(ngram) search against a real MySQL — the MATCH() clause, the author-handle
// subquery, relevance ranking and Korean partial matching only show up at query time, so a mock
// unit test can't catch a malformed clause. search_text is populated the way the write path does it
// (via PostSearchTextFlattener) so these posts mirror production rows.
// ★ NOT @Transactional (unlike most integration tests here). InnoDB FULLTEXT is invisible to a
// MATCH() that runs inside the SAME transaction as the INSERT — the just-inserted document sits in
// an FTS cache that only becomes searchable on commit. A rollback-per-test model would make every
// MATCH find nothing. So each write commits (Spring Data save() is its own tx), and cleanUp
// truncates the tables afterward.
@SpringBootTest
@ActiveProfiles("test")
class PublicFeedSearchIntegrationTest {

  @Autowired private PublicFeedQueryService service;
  @Autowired private PostRepository postRepository;
  @Autowired private PostSearchTextRepository postSearchTextRepository;
  @Autowired private PostViewEventRepository postViewEventRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private CreatePostUseCase createPost;
  @Autowired private PublishPostUseCase publishPost;
  @Autowired private JdbcTemplate jdbc;

  private final PostSearchTextFlattener flattener =
      new PostSearchTextFlattener(JsonMapper.builder().build());

  @AfterEach
  void cleanUp() {
    // Committed rows must be cleared between tests (no rollback). Child tables first for the FKs.
    jdbc.update("DELETE FROM post_view_event");
    jdbc.update("DELETE FROM post_block");
    jdbc.update("DELETE FROM post_search_text");
    jdbc.update("DELETE FROM post_tag");
    // 실 발행 경로(PublishPostUseCase)를 태우는 테스트는 발행 시 리비전 스냅샷을 남기므로 posts 를 지우기 전에 정리한다.
    jdbc.update("DELETE FROM post_revision");
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

  // Publish with body blocks — persists the derived search text to the post_search_text side table
  // exactly as the write use cases do (via upsert), so MATCH(search_text) reaches title + excerpt +
  // tags + body. The side table row is keyed by the saved post's id.
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
    p.publish();
    PostEntity saved = postRepository.save(p);
    postSearchTextRepository.upsert(saved.getId(), flattener.flatten(title, excerpt, tags, blocks));
  }

  private static PostBlockEntity paragraph(String text) {
    return new PostBlockEntity(null, PostBlockType.PARAGRAPH, text, 0);
  }

  private List<String> slugs(String query) {
    return service.feed(PublicFeedQuery.from(query, null, "recent", null, 0, 20)).items().stream()
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

    assertThat(slugs("hexagonal")).containsExactly("hexagonal");
    assertThat(slugs("hooks")).containsExactly("react-hooks");
    assertThat(slugs("frontend")).containsExactly("react-hooks");
    assertThat(slugs("alice")).containsExactlyInAnyOrder("hexagonal", "k8s");
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
    postRepository.save(draft);

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
    postRepository.save(draft);

    assertThat(service.suggestedAuthors(5)).isEmpty();
  }

  private long publishReturningId(
      long userId, String slug, String title, List<String> tags, int lifetimeViews) {
    PostEntity p = new PostEntity(userId, slug, title, "ko");
    p.updateTags(tags);
    for (int i = 0; i < lifetimeViews; i++) p.incrementViewCount();
    p.publish();
    long id = postRepository.save(p).getId();
    postSearchTextRepository.upsert(id, flattener.flatten(title, null, tags, List.of()));
    return id;
  }

  private void view(long postId, Instant at) {
    postViewEventRepository.save(new PostViewEventEntity(postId, at));
  }

  private List<String> trendingSlugs(String query) {
    return service.feed(PublicFeedQuery.from(query, null, "trending", null, 0, 20)).items().stream()
        .map(PublicFeedItem::slug)
        .toList();
  }

  @Test
  void trendingSearchRanksByRecentWindowViewsNotLifetimeCount() {
    long a = author("searchtrend");
    Instant now = Instant.now();
    Instant inWindow = now.minus(2, ChronoUnit.HOURS);
    Instant outOfWindow = now.minus(10, ChronoUnit.DAYS);

    // 세 글 모두 제목이 kotlin과 맞으니 trending 정렬은 누적 view_count가 아니라 최근 구간 조회수를 따라야 한다.
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
    return service
        .feed(PublicFeedQuery.from(query, null, "relevance", null, 0, 20))
        .items()
        .stream()
        .map(PublicFeedItem::slug)
        .toList();
  }

  // 예전 검색은 본문(post_block)을 통째로 놓쳤다. 본문 단어로도 글이 잡혀야 한다.
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
    assertThat(relevanceSlugs("리다이렉트")).containsExactly("with-body");
    assertThat(relevanceSlugs("캐시")).containsExactly("with-body");
  }

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

  @Test
  void koreanPartialMatch() {
    long a = author("kwriter");
    publish(
        a, "korean", "데이터베이스 인덱스 설계", null, List.of(), List.of(paragraph("인덱스 선택도와 카디널리티에 대하여")));
    assertThat(relevanceSlugs("인덱")).containsExactly("korean");
    assertThat(relevanceSlugs("데이터")).containsExactly("korean");
  }

  @Test
  void defaultRelevanceSortStillMatchesTitleTagAndAuthor() {
    long alice = author("aliceR");
    long bob = author("bobR");
    publish(alice, "r-hex", "Hexagonal in Spring", "ports", List.of("spring"));
    publish(bob, "r-react", "Learning React", "hooks", List.of("frontend"));

    assertThat(relevanceSlugs("hexagonal")).containsExactly("r-hex");
    assertThat(relevanceSlugs("frontend")).containsExactly("r-react");
    assertThat(relevanceSlugs("aliceR")).containsExactly("r-hex");
  }

  // 제목만 붙이고 본문 블록을 편집하지 않은 채 발행한 글도 제목 단어로 검색돼야 한다.
  // 편집 경로만 검색 평문을 채우면 이런 글은 곁 테이블에 행이 없어 검색되지 않는다.
  @Test
  void publishedWithoutBlockEditIsSearchableByTitleWord() {
    long author = author("publisher");
    var created =
        createPost.execute(new CreatePostCommand(author, "publish-only", "동시성 제어 깊이 파보기", "ko"));
    publishPost.execute(new PublishPostCommand(author, created.id()));

    Integer rows =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM post_search_text WHERE post_id = ?", Integer.class, created.id());
    assertThat(rows).isEqualTo(1);
    assertThat(relevanceSlugs("동시성")).containsExactly("publish-only");
    assertThat(relevanceSlugs("제어")).containsExactly("publish-only");
  }

  // 회귀 방지(짧은 질의): ngram(token size 2) 은 두 글자 미만 토큰을 인덱싱하지 않아 "C++"→"C" 같은 질의는 MATCH
  // 0건이 된다. 제목·요약 LIKE 폴백이 이런 짧은 질의를 제목에서 잡아, 예전 title LIKE 검색 대비 회귀를 막는다.
  @Test
  void shortQueryFallsBackToTitleLike() {
    long a = author("shorty");
    publish(a, "cpp", "C++ 메모리 모델 정리", "동시성", List.of("cpp"));
    publish(a, "rust", "Rust 소유권 이야기", "메모리", List.of("rust"));

    // "C++" 는 스크럽 후 "C"(1자)만 남아 ngram 매칭이 불가능 — 제목 LIKE 폴백으로 잡혀야 한다.
    assertThat(relevanceSlugs("C++")).containsExactly("cpp");
    assertThat(relevanceSlugs("C++")).doesNotContain("rust");
  }

  // 짧은 질의 폴백이 "일반 질의" 의 매칭을 넓히지 않는지 — 두 글자 이상 토큰이 하나라도 있으면 폴백은 꺼진다(본문/제목 ngram
  // 매칭만). 제목에 우연히 부분 문자열이 있어도 폴백 LIKE 로 새 매칭이 생기면 안 된다.
  @Test
  void normalQueryDoesNotTriggerTitleLikeFallback() {
    long a = author("normie");
    // 본문·제목·태그 어디에도 "인덱스" 없음. 다만 제목에 "인덱" 이라는 부분 문자열은 없다 — 폴백이 꺼져 있으니 0건이어야 한다.
    publish(a, "db", "데이터 정합성", "트랜잭션 격리", List.of("db"));

    assertThat(relevanceSlugs("존재하지않는단어")).isEmpty();
  }
}
