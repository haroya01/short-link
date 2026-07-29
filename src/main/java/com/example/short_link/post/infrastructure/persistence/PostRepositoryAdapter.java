package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.AuthorPostStats;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostPerformanceSort;
import com.example.short_link.post.domain.PostStatus;
import com.example.short_link.post.domain.SeriesActivity;
import com.example.short_link.post.domain.TagCount;
import com.example.short_link.post.domain.repository.PostRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class PostRepositoryAdapter implements PostRepository {

  // Rolling window the trending feed ranks views over — "recent traction" = the last 7 days.
  private static final Duration TRENDING_WINDOW = Duration.ofDays(7);

  private final JpaPostRepository jpa;

  @Override
  public Optional<PostEntity> findById(Long id) {
    return jpa.findById(id);
  }

  @Override
  public List<PostEntity> findAllByIdIn(Collection<Long> ids) {
    return jpa.findAllByIdIn(ids);
  }

  @Override
  public Optional<PostEntity> findByUserIdAndSlug(Long userId, String slug) {
    return jpa.findByUserIdAndSlug(userId, slug);
  }

  @Override
  public Optional<PostEntity> findByPreviewToken(String previewToken) {
    return jpa.findByPreviewToken(previewToken);
  }

  @Override
  public PostEntity save(PostEntity post) {
    return jpa.save(post);
  }

  @Override
  public void delete(PostEntity post) {
    jpa.delete(post);
  }

  @Override
  public void incrementLikeCount(Long postId) {
    jpa.incrementLikeCount(postId);
  }

  @Override
  public void decrementLikeCount(Long postId) {
    jpa.decrementLikeCount(postId);
  }

  @Override
  public boolean existsByUserIdAndSlug(Long userId, String slug) {
    return jpa.existsByUserIdAndSlug(userId, slug);
  }

  @Override
  public List<PostEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId) {
    return jpa.findAllByUserIdOrderByCreatedAtDesc(userId);
  }

  @Override
  public List<PostEntity> findAllByUserIdAndStatusOrderByPublishedAtDesc(
      Long userId, PostStatus status) {
    return jpa.findAllByUserIdAndStatusOrderByPublishedAtDesc(userId, status);
  }

  // Posts with analytics meaning — drafts/scheduled have never been read, so they're excluded.
  private static final List<PostStatus> ANALYTICS_STATUSES =
      List.of(PostStatus.PUBLISHED, PostStatus.UNPUBLISHED);

  @Override
  public List<PostEntity> findUserAnalyticsPosts(
      Long userId, int page, int size, PostPerformanceSort sort) {
    String field =
        switch (sort) {
          case LIKES -> "likeCount";
          case RECENT -> "createdAt";
          case VIEWS -> "viewCount";
        };
    // id-desc tie-break keeps paging stable when many posts share the same metric value (e.g. all
    // 0).
    Sort ordering = Sort.by(Sort.Order.desc(field), Sort.Order.desc("id"));
    return jpa.findByUserIdAndStatusIn(
        userId, ANALYTICS_STATUSES, PageRequest.of(page, size, ordering));
  }

  @Override
  public long countUserAnalyticsPosts(Long userId) {
    return jpa.countByUserIdAndStatusIn(userId, ANALYTICS_STATUSES);
  }

  @Override
  public List<PostEntity> findScheduledDue(Instant now) {
    return jpa.findAllByStatusAndScheduledAtLessThanEqual(PostStatus.SCHEDULED, now);
  }

  @Override
  public List<PostEntity> findAllBySeriesIdOrderBySeriesOrderAsc(Long seriesId) {
    return jpa.findAllBySeriesIdOrderBySeriesOrderAsc(seriesId);
  }

  @Override
  public List<PostEntity> findAllBySeriesIdInOrderBySeriesOrderAsc(Collection<Long> seriesIds) {
    if (seriesIds.isEmpty()) {
      return List.of();
    }
    return jpa.findAllBySeriesIdInOrderBySeriesOrderAsc(seriesIds);
  }

  @Override
  public List<PostEntity> findAllBySeriesIdAndStatusOrderBySeriesOrderAsc(
      Long seriesId, PostStatus status) {
    return jpa.findAllBySeriesIdAndStatusOrderBySeriesOrderAsc(seriesId, status);
  }

  @Override
  public List<PostEntity> findPublishedRecent(String lang, int page, int size) {
    return jpa.findPublishedRecent(
        PostStatus.PUBLISHED, normLang(lang), PageRequest.of(page, size));
  }

  @Override
  public List<PostEntity> findPublishedTrending(String lang, int page, int size) {
    Instant since = Instant.now().minus(TRENDING_WINDOW);
    return jpa.findPublishedTrendingSince(since, normLang(lang), PageRequest.of(page, size));
  }

  @Override
  public long countPublished(String lang) {
    return jpa.countPublishedByLang(PostStatus.PUBLISHED, normLang(lang));
  }

  @Override
  public long countPublishedByUserId(Long userId) {
    return jpa.countByUserIdAndStatus(userId, PostStatus.PUBLISHED);
  }

  @Override
  public List<PostEntity> findPublishedByTag(String tag, int page, int size) {
    return jpa.findPublishedByTag(tag, PostStatus.PUBLISHED, PageRequest.of(page, size));
  }

  @Override
  public long countPublishedByTag(String tag) {
    return jpa.countPublishedByTag(tag, PostStatus.PUBLISHED);
  }

  @Override
  public List<PostEntity> searchPublishedByRelevance(
      String query, String lang, int page, int size) {
    return jpa.searchPublishedByRelevance(
        booleanMatch(query),
        likePattern(query),
        titleLikeFallback(query),
        normLang(lang),
        PageRequest.of(page, size));
  }

  @Override
  public List<PostEntity> searchPublished(String query, String lang, int page, int size) {
    return jpa.searchPublishedRecent(
        booleanMatch(query),
        likePattern(query),
        titleLikeFallback(query),
        normLang(lang),
        PageRequest.of(page, size));
  }

  @Override
  public List<PostEntity> searchPublishedTrending(String query, String lang, int page, int size) {
    Instant since = Instant.now().minus(TRENDING_WINDOW);
    return jpa.searchPublishedTrendingSince(
        booleanMatch(query),
        likePattern(query),
        titleLikeFallback(query),
        since,
        normLang(lang),
        PageRequest.of(page, size));
  }

  @Override
  public long countSearchPublished(String query, String lang) {
    return jpa.countSearchPublished(
        booleanMatch(query), likePattern(query), titleLikeFallback(query), normLang(lang));
  }

  /**
   * Blank/whitespace language → null (no filter). Keeps "all languages" the empty-string default.
   */
  private static String normLang(String lang) {
    return lang == null || lang.isBlank() ? null : lang.trim();
  }

  // Lowercase + escape the LIKE metacharacters in user input, then wrap in %…% for a contains
  // match.
  // '!' is the escape char declared in the queries; escape it first so a literal '!' can't shield
  // the
  // following char. Without this, a search for "50%" would match every title.
  private static String likePattern(String query) {
    String escaped = query.toLowerCase().replace("!", "!!").replace("%", "!%").replace("_", "!_");
    return "%" + escaped + "%";
  }

  // Normalize raw user input into the AGAINST string for FULLTEXT BOOLEAN MODE. We strip the
  // operator characters (+ - > < ( ) ~ * " @) so a stray operator can't hijack the query or
  // become an unbalanced token, and collapse whitespace, leaving PLAIN space-separated terms.
  // Plain (no +/*/quote) BOOLEAN terms are the one form that matches both Korean and English
  // precisely under the ngram parser: a term's bigrams are required as a group (real substring
  // match), unlike NATURAL mode which over-matches on any shared bigram. An empty result (query
  // was only operators/punctuation) stays empty — AGAINST('') matches zero rows without error.
  static String booleanMatch(String query) {
    return query.replaceAll("[+\\-><()~*\"@]", " ").replaceAll("\\s+", " ").trim();
  }

  // InnoDB 기본 스톱워드 중 1·2글자 항목. ngram 파서는 "스톱워드를 포함하는 토큰"을 인덱스에서
  // 제외하므로(MySQL 공식 문서), 바이그램(2글자 토큰)에 실제로 걸릴 수 있는 스톱워드는 이 부분집합뿐
  // — 3글자 이상(the·com·for…)은 2글자 토큰에 포함될 수 없다. 'a'·'i' 는 substring 포함만으로 걸린다.
  private static final java.util.Set<String> NGRAM_FATAL_BIGRAMS =
      java.util.Set.of(
          "an", "as", "at", "be", "by", "de", "en", "in", "is", "it", "la", "of", "on", "or", "to");

  // 용어가 FULLTEXT(ngram) 인덱스에 "보이는지". 모든 바이그램이 기본 스톱워드에 오염된 용어는 색인
  // 자체가 0개라 MATCH 가 영원히 못 잡는다 — "java"(ja·av·va 전부 'a' 포함)·"data"(da·at·ta) 가
  // 프로드 실측 0건, "jpa"("jp" 생존)·"docker" 정상(2026-07-29). 서버측 정석 해법은 빈 스톱워드
  // 테이블 + FULLTEXT 인덱스 리빌드(DB 설정 = 직영)지만, 그때까지 앱 폴백이 구제한다.
  private static boolean visibleToNgram(String term) {
    if (term.length() < 2) return false; // ngram(2) 은 두 글자 미만 토큰을 인덱싱하지 않는다.
    String lower = term.toLowerCase();
    for (int i = 0; i + 2 <= lower.length(); i++) {
      String bigram = lower.substring(i, i + 2);
      boolean contaminated =
          bigram.indexOf('a') >= 0
              || bigram.indexOf('i') >= 0
              || NGRAM_FATAL_BIGRAMS.contains(bigram);
      if (!contaminated) return true;
    }
    return false;
  }

  // 모든 토큰이 인덱스 불가시(2자 미만이거나 스톱워드 전멸)일 때에 한해 제목·요약 LIKE 폴백을 켠다 —
  // "C++"·한 글자 질의뿐 아니라 "java"·"data" 같은 최빈 개발 검색어가 여기 해당한다. 하나라도 보이는
  // 토큰이 있으면 MATCH 가 담당하므로 null 을 넘겨(쿼리에서 IS NULL 로 가지 자체를 꺼) 일반 질의의
  // 매칭 범위·성능에 영향을 주지 않는다.
  static String titleLikeFallback(String query) {
    String scrubbed = booleanMatch(query);
    if (scrubbed.isEmpty()) {
      // 연산자·구두점만 있어 매칭할 자연어가 없으면 폴백해도 잡을 게 없다.
      return null;
    }
    for (String term : scrubbed.split(" ")) {
      if (visibleToNgram(term)) {
        return null;
      }
    }
    // 원문(스크럽 전)을 이스케이프해 %…% 로 감싼다 — 사용자가 친 그대로(예: "C++")를 부분일치로 찾는다.
    return likePattern(query);
  }

  @Override
  public List<PostEntity> findPublishedByAuthorsSeriesOrTags(
      Collection<Long> authorIds,
      Collection<Long> seriesIds,
      Collection<String> tags,
      int page,
      int size) {
    return jpa.findPublishedByAuthorsSeriesOrTags(
        authorIds, seriesIds, tags, PostStatus.PUBLISHED, PageRequest.of(page, size));
  }

  @Override
  public long countPublishedByAuthorsSeriesOrTags(
      Collection<Long> authorIds, Collection<Long> seriesIds, Collection<String> tags) {
    return jpa.countPublishedByAuthorsSeriesOrTags(
        authorIds, seriesIds, tags, PostStatus.PUBLISHED);
  }

  @Override
  public List<PostEntity> findForYouCandidates(
      Long userId, Collection<String> tags, Collection<Long> excludeIds, int page, int size) {
    return jpa.findForYouCandidates(
        userId, tags, excludeIds, PostStatus.PUBLISHED, PageRequest.of(page, size));
  }

  @Override
  public long countForYouCandidates(
      Long userId, Collection<String> tags, Collection<Long> excludeIds) {
    return jpa.countForYouCandidates(userId, tags, excludeIds, PostStatus.PUBLISHED);
  }

  @Override
  public List<TagCount> findPopularTags(int limit) {
    return jpa.findPopularTags(PostStatus.PUBLISHED, PageRequest.of(0, limit)).stream()
        .map(row -> new TagCount((String) row[0], ((Number) row[1]).longValue()))
        .toList();
  }

  @Override
  public List<AuthorPostStats> findTopAuthorStats(int limit) {
    return jpa.findTopAuthorIds(PostStatus.PUBLISHED, PageRequest.of(0, limit)).stream()
        .map(
            row ->
                new AuthorPostStats(
                    ((Number) row[0]).longValue(),
                    ((Number) row[1]).longValue(),
                    ((Number) row[2]).longValue()))
        .toList();
  }

  @Override
  public List<SeriesActivity> findActiveSeries(int minPosts, int limit) {
    return jpa.findActiveSeries(PostStatus.PUBLISHED, minPosts, PageRequest.of(0, limit)).stream()
        .map(
            row ->
                new SeriesActivity(
                    ((Number) row[0]).longValue(), ((Number) row[1]).longValue(), (Instant) row[2]))
        .toList();
  }
}
