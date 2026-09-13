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
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class PostRepositoryAdapter implements PostRepository {

  private static final Duration TRENDING_WINDOW = Duration.ofDays(7);

  private final JpaPostRepository jpa;

  @Override
  public Optional<PostEntity> findById(Long id) {
    return jpa.findById(id);
  }

  @Override
  public Optional<PostEntity> findByIdForUpdate(Long id) {
    return jpa.findByIdForUpdate(id);
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
  public Optional<PostEntity> findByUserIdAndSlugForUpdate(Long userId, String slug) {
    return jpa.findByUserIdAndSlugForUpdate(userId, slug);
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
  public void flush() {
    jpa.flush();
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
    Sort ordering = Sort.by(Sort.Order.desc(field), Sort.Order.desc("id"));
    return jpa.findByUserIdAndStatusIn(
        userId, ANALYTICS_STATUSES, PageRequest.of(page, size, ordering));
  }

  @Override
  public long countUserAnalyticsPosts(Long userId) {
    return jpa.countByUserIdAndStatusIn(userId, ANALYTICS_STATUSES);
  }

  @Override
  public List<Long> findScheduledDueIds(Instant now) {
    return jpa.findScheduledDueIds(PostStatus.SCHEDULED, now);
  }

  @Override
  public List<PostEntity> findAllBySeriesIdOrderBySeriesOrderAsc(Long seriesId) {
    return jpa.findAllBySeriesIdOrderBySeriesOrderAsc(seriesId);
  }

  @Override
  public List<PostEntity> findSeriesMembersAndRequestedForUpdate(
      Long seriesId, Collection<Long> requestedIds) {
    return jpa.findSeriesMembersAndRequestedForUpdate(seriesId, idsForIn(requestedIds));
  }

  @Override
  public List<PostEntity> findPublishedByUserIdForUpdate(Long userId) {
    return jpa.findPublishedByUserIdForUpdate(userId, PostStatus.PUBLISHED);
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

  private static String normLang(String lang) {
    return lang == null || lang.isBlank() ? null : lang.trim();
  }

  // LIKE의 이스케이프 문자 !부터 처리해야 사용자의 !가 다음 문자에 영향을 주지 않는다.
  private static String likePattern(String query) {
    String escaped =
        query.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_");
    return "%" + escaped + "%";
  }

  // BOOLEAN 연산자를 제거해 검색어가 구문을 바꾸지 않게 한다.
  // ngram의 한·영 부분 일치를 유지하려고 접두·구문 연산자 없이 평문 항만 전달한다.
  static String booleanMatch(String query) {
    return query.replaceAll("[+\\-><()~*\"@]", " ").replaceAll("\\s+", " ").trim();
  }

  // 2글자 ngram이 처리하지 못하는 짧은 질의만 제목·요약 LIKE 폴백을 사용한다.
  static String titleLikeFallback(String query) {
    String scrubbed = booleanMatch(query);
    if (scrubbed.isEmpty()) {
      return null;
    }
    boolean allTermsTooShort = true;
    for (String term : scrubbed.split(" ")) {
      if (term.length() >= 2) {
        allTermsTooShort = false;
        break;
      }
    }
    // C++ 같은 원문을 그대로 찾도록 연산자 제거 전 검색어를 이스케이프한다.
    return allTermsTooShort ? likePattern(query) : null;
  }

  @Override
  public List<PostEntity> findPublishedByAuthorsSeriesOrTags(
      Collection<Long> authorIds,
      Collection<Long> seriesIds,
      Collection<String> tags,
      int page,
      int size) {
    return jpa.findPublishedByAuthorsSeriesOrTags(
        idsForIn(authorIds),
        idsForIn(seriesIds),
        tagsForIn(tags),
        PostStatus.PUBLISHED,
        PageRequest.of(page, size));
  }

  @Override
  public long countPublishedByAuthorsSeriesOrTags(
      Collection<Long> authorIds, Collection<Long> seriesIds, Collection<String> tags) {
    return jpa.countPublishedByAuthorsSeriesOrTags(
        idsForIn(authorIds), idsForIn(seriesIds), tagsForIn(tags), PostStatus.PUBLISHED);
  }

  @Override
  public List<PostEntity> findForYouCandidates(
      Long userId, Collection<String> tags, Collection<Long> excludeIds, int page, int size) {
    return jpa.findForYouCandidates(
        userId,
        tagsForIn(tags),
        idsForIn(excludeIds),
        PostStatus.PUBLISHED,
        PageRequest.of(page, size));
  }

  @Override
  public long countForYouCandidates(
      Long userId, Collection<String> tags, Collection<Long> excludeIds) {
    return jpa.countForYouCandidates(
        userId, tagsForIn(tags), idsForIn(excludeIds), PostStatus.PUBLISHED);
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

  // JPQL의 빈 IN/NOT IN 인수 표현은 저장소 구현의 책임이다.
  private static Collection<Long> idsForIn(Collection<Long> ids) {
    return ids.isEmpty() ? List.of(-1L) : ids;
  }

  private static Collection<String> tagsForIn(Collection<String> tags) {
    return tags.isEmpty() ? List.of("\u0000") : tags;
  }
}
