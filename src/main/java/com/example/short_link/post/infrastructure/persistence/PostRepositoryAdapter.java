package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.AuthorPostStats;
import com.example.short_link.post.domain.DiscoveryQuality;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostPerformanceSort;
import com.example.short_link.post.domain.PostStatus;
import com.example.short_link.post.domain.SeriesActivity;
import com.example.short_link.post.domain.TagCount;
import com.example.short_link.post.domain.feed.FeedCandidate;
import com.example.short_link.post.domain.feed.FeedRanking;
import com.example.short_link.post.domain.repository.PostRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class PostRepositoryAdapter implements PostRepository {

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
        PostStatus.PUBLISHED,
        normLang(lang),
        DiscoveryQuality.MIN_BODY_TEXT_LENGTH,
        PageRequest.of(page, size));
  }

  @Override
  public List<PostEntity> findPublishedTrending(String lang, int page, int size) {
    Instant since = Instant.now().minus(FeedRanking.TRENDING_WINDOW);
    return jpa.findPublishedTrendingSince(
        since, normLang(lang), DiscoveryQuality.MIN_BODY_TEXT_LENGTH, PageRequest.of(page, size));
  }

  @Override
  public long countPublished(String lang) {
    return jpa.countPublishedByLang(
        PostStatus.PUBLISHED, normLang(lang), DiscoveryQuality.MIN_BODY_TEXT_LENGTH);
  }

  @Override
  public long countPublishedByUserId(Long userId) {
    return jpa.countByUserIdAndStatus(userId, PostStatus.PUBLISHED);
  }

  @Override
  public List<PostEntity> findPublishedByTag(String tag, int page, int size) {
    return jpa.findPublishedByTag(
        tag,
        PostStatus.PUBLISHED,
        DiscoveryQuality.MIN_BODY_TEXT_LENGTH,
        PageRequest.of(page, size));
  }

  @Override
  public long countPublishedByTag(String tag) {
    return jpa.countPublishedByTag(
        tag, PostStatus.PUBLISHED, DiscoveryQuality.MIN_BODY_TEXT_LENGTH);
  }

  @Override
  public List<PostEntity> searchPublishedByRelevance(
      String query, String lang, int page, int size) {
    return jpa.searchPublishedByRelevance(
        booleanMatch(query),
        likePattern(query),
        titleLikeFallback(query),
        titleWordFallback(query),
        normLang(lang),
        PageRequest.of(page, size));
  }

  @Override
  public List<PostEntity> searchPublished(String query, String lang, int page, int size) {
    return jpa.searchPublishedRecent(
        booleanMatch(query),
        likePattern(query),
        titleLikeFallback(query),
        titleWordFallback(query),
        normLang(lang),
        PageRequest.of(page, size));
  }

  @Override
  public List<PostEntity> searchPublishedTrending(String query, String lang, int page, int size) {
    Instant since = Instant.now().minus(FeedRanking.TRENDING_WINDOW);
    return jpa.searchPublishedTrendingSince(
        booleanMatch(query),
        likePattern(query),
        titleLikeFallback(query),
        titleWordFallback(query),
        since,
        normLang(lang),
        PageRequest.of(page, size));
  }

  @Override
  public long countSearchPublished(String query, String lang) {
    return jpa.countSearchPublished(
        booleanMatch(query),
        likePattern(query),
        titleLikeFallback(query),
        titleWordFallback(query),
        normLang(lang));
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

  // ngram 파서는 기본 스톱워드를 포함한 토큰을 색인하지 않고 토큰보다 긴 스톱워드는 무시한다.
  // 한 글자 스톱워드 'a'·'i'는 포함 여부로, 두 글자 스톱워드는 이 목록과의 일치로 판정한다.
  private static final Set<String> NGRAM_FATAL_BIGRAMS =
      Set.of(
          "an", "as", "at", "be", "by", "de", "en", "in", "is", "it", "la", "of", "on", "or", "to");

  private static boolean visibleToNgram(String term) {
    if (term.length() < 2) return false;
    String lower = term.toLowerCase(Locale.ROOT);
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

  // 두 글자 미만이거나 모든 바이그램이 스톱워드에 걸려 ngram 색인에 남지 않는 항만으로 된 질의만
  // 제목·요약 LIKE 폴백을 사용한다.
  static String titleLikeFallback(String query) {
    String scrubbed = booleanMatch(query);
    if (scrubbed.isEmpty()) {
      return null;
    }
    for (String term : scrubbed.split(" ")) {
      if (visibleToNgram(term)) {
        return null;
      }
    }
    // C++ 같은 원문을 그대로 찾도록 연산자 제거 전 검색어를 이스케이프한다.
    return likePattern(query);
  }

  // 영문·숫자로 시작하거나 끝나는 폴백 질의는 낱말 속(email·domain의 ai, javascript의 java)에
  // 걸리지 않게 앞뒤가 영문·숫자가 아닐 때만 맞춘다. 한글 조사가 붙은 'AI가'·'Java로'는 걸린다.
  static String titleWordFallback(String query) {
    if (titleLikeFallback(query) == null) {
      return null;
    }
    String phrase = query.toLowerCase(Locale.ROOT);
    boolean head = isAsciiAlnum(phrase.charAt(0));
    boolean tail = isAsciiAlnum(phrase.charAt(phrase.length() - 1));
    if (!head && !tail) {
      return null;
    }
    return (head ? "(^|[^a-z0-9])" : "") + regexLiteral(phrase) + (tail ? "([^a-z0-9]|$)" : "");
  }

  private static boolean isAsciiAlnum(char c) {
    return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
  }

  private static String regexLiteral(String text) {
    StringBuilder out = new StringBuilder(text.length() * 2);
    for (char c : text.toCharArray()) {
      if ("\\^$.|?*+()[]{}".indexOf(c) >= 0) {
        out.append('\\');
      }
      out.append(c);
    }
    return out.toString();
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
  public List<FeedCandidate> findFeedCandidates(int limit) {
    List<Object[]> rows =
        jpa.findFeedCandidateRows(
            PostStatus.PUBLISHED, DiscoveryQuality.MIN_BODY_TEXT_LENGTH, PageRequest.of(0, limit));
    if (rows.isEmpty()) {
      return List.of();
    }
    Map<Long, List<String>> tagsById = new HashMap<>();
    for (Object[] row : jpa.findTagRowsByPostIdIn(rows.stream().map(r -> (Long) r[0]).toList())) {
      tagsById.computeIfAbsent((Long) row[0], id -> new ArrayList<>()).add((String) row[1]);
    }
    return rows.stream()
        .map(
            row ->
                new FeedCandidate(
                    (Long) row[0],
                    (Long) row[1],
                    tagsById.getOrDefault((Long) row[0], List.of()),
                    (String) row[2],
                    (Instant) row[3],
                    (Long) row[4]))
        .toList();
  }

  @Override
  public List<TagCount> findPopularTags(int limit) {
    return jpa
        .findPopularTags(
            PostStatus.PUBLISHED, DiscoveryQuality.MIN_BODY_TEXT_LENGTH, PageRequest.of(0, limit))
        .stream()
        .map(row -> new TagCount((String) row[0], ((Number) row[1]).longValue()))
        .toList();
  }

  @Override
  public List<AuthorPostStats> findTopAuthorStats(int limit) {
    return jpa
        .findTopAuthorIds(
            PostStatus.PUBLISHED, DiscoveryQuality.MIN_BODY_TEXT_LENGTH, PageRequest.of(0, limit))
        .stream()
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
    return jpa
        .findActiveSeries(
            PostStatus.PUBLISHED,
            minPosts,
            DiscoveryQuality.MIN_BODY_TEXT_LENGTH,
            PageRequest.of(0, limit))
        .stream()
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
