package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.common.post.PostAccessSql;
import com.example.short_link.common.user.HeardSql;
import com.example.short_link.post.domain.AuthorPostStats;
import com.example.short_link.post.domain.DiscoveryQuality;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostPerformanceSort;
import com.example.short_link.post.domain.PostStatus;
import com.example.short_link.post.domain.TagCount;
import com.example.short_link.post.domain.repository.PostRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class PostRepositoryAdapter implements PostRepository {

  static final Duration TRENDING_WINDOW = Duration.ofDays(7);

  private final JpaPostRepository jpa;
  private final TrendingPostRanking ranking;

  @PersistenceContext private EntityManager em;

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
  public Optional<PostEntity> findUnblockedByUserIdAndSlug(
      Long userId, String slug, Long viewerId) {
    return jpa.findUnblockedByUserIdAndSlug(userId, slug, HeardSql.viewer(viewerId));
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
  public List<PostEntity> findProfilePosts(Long userId, Long viewerId) {
    return jpa.findProfilePosts(userId, HeardSql.viewer(viewerId));
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
  public List<PostEntity> findOpenBySeriesIdOrderBySeriesOrderAsc(Long seriesId) {
    return jpa.findOpenBySeriesId(seriesId);
  }

  @Override
  public List<PostEntity> findPublishedRecent(Long viewerId, String lang, int page, int size) {
    return jpa.findPublishedRecent(
        normLang(lang),
        DiscoveryQuality.MIN_BODY_TEXT_LENGTH,
        HeardSql.viewer(viewerId),
        Instant.now(),
        PageRequest.of(page, size));
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<PostEntity> findHomeRecentAt(Long viewerId, int offset, int limit) {
    return em.createNativeQuery(
            "SELECT p.* FROM posts p WHERE ("
                + JpaPostRepository.BROWSE_CONDITION
                + ") OR ("
                + PostAccessSql.inFeedRooms("p")
                + HeardSql.POST_AUTHOR
                + ") ORDER BY p.published_at DESC",
            PostEntity.class)
        .setParameter("lang", null)
        .setParameter("minBody", DiscoveryQuality.MIN_BODY_TEXT_LENGTH)
        .setParameter("viewer", HeardSql.viewer(viewerId))
        .setParameter("now", Instant.now())
        .setFirstResult(offset)
        .setMaxResults(limit)
        .getResultList();
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<PostEntity> findInRoomAt(Long roomId, Long viewerId, int offset, int limit) {
    return em.createNativeQuery(
            "SELECT p.* FROM posts p WHERE "
                + PostAccessSql.inRoom("p", ":room")
                + HeardSql.POST_AUTHOR
                + " ORDER BY p.published_at DESC, p.id DESC",
            PostEntity.class)
        .setParameter("room", roomId)
        .setParameter("viewer", HeardSql.viewer(viewerId))
        .setParameter("now", Instant.now())
        .setFirstResult(offset)
        .setMaxResults(limit)
        .getResultList();
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<PostEntity> findInRoomsOfAt(Long viewerId, int offset, int limit) {
    return em.createNativeQuery(
            "SELECT p.* FROM posts p WHERE "
                + PostAccessSql.inRoomsOf("p")
                + HeardSql.POST_AUTHOR
                + " ORDER BY p.published_at DESC, p.id DESC",
            PostEntity.class)
        .setParameter("viewer", HeardSql.viewer(viewerId))
        .setParameter("now", Instant.now())
        .setFirstResult(offset)
        .setMaxResults(limit)
        .getResultList();
  }

  @Override
  @SuppressWarnings("unchecked")
  public Map<Long, CardMarks> cardMarks(Collection<Long> postIds, Long viewerId) {
    if (postIds.isEmpty()) {
      return Map.of();
    }
    List<Object[]> rows =
        em.createNativeQuery(
                "SELECT p.id, (SELECT COUNT(*) FROM comment c WHERE c.post_id = p.id"
                    + " AND c.deleted_at IS NULL AND "
                    + PostAccessSql.said("c")
                    + "), EXISTS (SELECT 1 FROM post_like l"
                    + " WHERE l.post_id = p.id AND l.user_id = :viewer)"
                    + " FROM posts p WHERE p.id IN (:ids)")
            .setParameter("ids", postIds)
            .setParameter("viewer", HeardSql.viewer(viewerId))
            .getResultList();
    Map<Long, CardMarks> marks = new HashMap<>();
    for (Object[] row : rows) {
      marks.put(
          ((Number) row[0]).longValue(),
          new CardMarks(((Number) row[1]).longValue(), ((Number) row[2]).intValue() == 1));
    }
    return marks;
  }

  @Override
  public List<PostEntity> findPublishedTrendingAt(Long viewerId, int offset, int limit) {
    return trending(viewerId, null, offset, limit);
  }

  private List<PostEntity> trending(Long viewerId, String lang, long offset, int limit) {
    Instant now = Instant.now();
    List<Long> ranked = ranking.ids(lang);
    List<PostEntity> shown =
        ranked.isEmpty()
            ? List.of()
            : jpa.findPublishedRankedAmong(
                ranked,
                ranked.stream().map(String::valueOf).collect(Collectors.joining(",")),
                lang,
                DiscoveryQuality.MIN_BODY_TEXT_LENGTH,
                HeardSql.viewer(viewerId),
                now,
                new OffsetLimit(offset, limit));
    if (shown.size() < limit && ranked.size() >= TrendingPostRanking.SIZE) {
      return jpa.findPublishedTrendingSince(
          now.minus(TRENDING_WINDOW),
          lang,
          DiscoveryQuality.MIN_BODY_TEXT_LENGTH,
          HeardSql.viewer(viewerId),
          now,
          new OffsetLimit(offset, limit));
    }
    return shown;
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<PostEntity> findHomeFollowingAt(
      Long viewerId,
      Collection<Long> authorIds,
      Collection<Long> seriesIds,
      Collection<String> tags,
      int offset,
      int limit) {
    boolean byAuthorsOnly = seriesIds.isEmpty() && tags.isEmpty();
    var query =
        em.createNativeQuery(
                (byAuthorsOnly
                        ? "SELECT p.* FROM posts p WHERE (("
                            + PostAccessSql.LISTED
                            + " AND p.user_id IN (:authorIds))"
                        : "SELECT DISTINCT p.* FROM posts p LEFT JOIN post_tag t ON t.post_id = p.id"
                            + " WHERE (("
                            + PostAccessSql.LISTED
                            + " AND (p.user_id IN (:authorIds) OR p.series_id IN (:seriesIds)"
                            + " OR LOWER(t.tag) IN (:tags)))")
                    + " OR ("
                    + PostAccessSql.inFeedRooms("p")
                    + "))"
                    + HeardSql.POST_AUTHOR
                    + " ORDER BY p.published_at DESC",
                PostEntity.class)
            .setParameter("authorIds", idsForIn(authorIds))
            .setParameter("viewer", HeardSql.viewer(viewerId))
            .setParameter("now", Instant.now());
    if (!byAuthorsOnly) {
      query.setParameter("seriesIds", idsForIn(seriesIds)).setParameter("tags", tagsForIn(tags));
    }
    return query.setFirstResult(offset).setMaxResults(limit).getResultList();
  }

  private List<PostEntity> followed(
      Long viewerId,
      Collection<Long> authorIds,
      Collection<Long> seriesIds,
      Collection<String> tags,
      Pageable pageable) {
    if (seriesIds.isEmpty() && tags.isEmpty()) {
      return jpa.findPublishedByAuthors(
          idsForIn(authorIds), HeardSql.viewer(viewerId), Instant.now(), pageable);
    }
    return jpa.findPublishedByAuthorsSeriesOrTags(
        idsForIn(authorIds),
        idsForIn(seriesIds),
        tagsForIn(tags),
        HeardSql.viewer(viewerId),
        Instant.now(),
        pageable);
  }

  @Override
  public List<PostEntity> findPublishedTrending(Long viewerId, String lang, int page, int size) {
    return trending(viewerId, normLang(lang), (long) page * size, size);
  }

  @Override
  public long countPublished(Long viewerId, String lang) {
    return jpa.countPublishedByLang(
        normLang(lang),
        DiscoveryQuality.MIN_BODY_TEXT_LENGTH,
        HeardSql.viewer(viewerId),
        Instant.now());
  }

  @Override
  public long countPublishedByUserId(Long userId) {
    return jpa.countCardsByUserId(userId);
  }

  @Override
  public List<PostEntity> findPublishedByTag(Long viewerId, String tag, int page, int size) {
    return jpa.findPublishedByTag(
        tag,
        DiscoveryQuality.MIN_BODY_TEXT_LENGTH,
        HeardSql.viewer(viewerId),
        Instant.now(),
        PageRequest.of(page, size));
  }

  @Override
  public List<PostEntity> findPublishedTrendingByTag(
      Long viewerId, String tag, int page, int size) {
    Instant now = Instant.now();
    return jpa.findPublishedTrendingByTagSince(
        tag,
        now.minus(TRENDING_WINDOW),
        DiscoveryQuality.MIN_BODY_TEXT_LENGTH,
        HeardSql.viewer(viewerId),
        now,
        PageRequest.of(page, size));
  }

  @Override
  public List<PostEntity> findPublishedQuotingNote(
      Long noteId, Long viewerId, int offset, int limit) {
    return jpa.findPublishedQuotingNote(
        noteId, HeardSql.viewer(viewerId), Instant.now(), offset, limit);
  }

  @Override
  public long countPublishedByTag(Long viewerId, String tag) {
    return jpa.countPublishedByTag(
        tag, DiscoveryQuality.MIN_BODY_TEXT_LENGTH, HeardSql.viewer(viewerId), Instant.now());
  }

  @Override
  public List<PostEntity> searchPublishedByRelevance(
      Long viewerId, String query, String lang, int page, int size) {
    return jpa.searchPublishedByRelevance(
        booleanMatch(query),
        matchWordBoundaries(query),
        likePattern(query),
        titleLikeFallback(query),
        titleWordFallback(query),
        normLang(lang),
        HeardSql.viewer(viewerId),
        Instant.now(),
        PageRequest.of(page, size));
  }

  @Override
  public List<PostEntity> searchPublished(
      Long viewerId, String query, String lang, int page, int size) {
    return jpa.searchPublishedRecent(
        booleanMatch(query),
        matchWordBoundaries(query),
        likePattern(query),
        titleLikeFallback(query),
        titleWordFallback(query),
        normLang(lang),
        HeardSql.viewer(viewerId),
        Instant.now(),
        PageRequest.of(page, size));
  }

  @Override
  public List<PostEntity> searchPublishedTrending(
      Long viewerId, String query, String lang, int page, int size) {
    Instant now = Instant.now();
    return jpa.searchPublishedTrendingSince(
        booleanMatch(query),
        matchWordBoundaries(query),
        likePattern(query),
        titleLikeFallback(query),
        titleWordFallback(query),
        now.minus(TRENDING_WINDOW),
        normLang(lang),
        HeardSql.viewer(viewerId),
        now,
        PageRequest.of(page, size));
  }

  @Override
  public long countSearchPublished(Long viewerId, String query, String lang) {
    return jpa.countSearchPublished(
        booleanMatch(query),
        matchWordBoundaries(query),
        likePattern(query),
        titleLikeFallback(query),
        titleWordFallback(query),
        normLang(lang),
        HeardSql.viewer(viewerId),
        Instant.now());
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

  // BOOLEAN 연산자와 해시태그 #을 지워 검색어가 구문을 바꾸지 않게 하고, 색인에 남는 항마다 +( )로
  // 모두 요구한다. 괄호 없는 +는 한 바이그램짜리 항(설계)을 찾지 못한다.
  static String booleanMatch(String query) {
    StringBuilder match = new StringBuilder();
    for (String term : scrub(query).split(" ")) {
      if (indexed(term)) {
        match.append(match.isEmpty() ? "" : " ").append("+(").append(term).append(')');
      }
    }
    return match.toString();
  }

  private static String scrub(String query) {
    return query.replaceAll("[+\\-><()~*\"@#]", " ").replaceAll("\\s+", " ").trim();
  }

  static String matchWordBoundaries(String query) {
    StringBuilder pattern = new StringBuilder();
    for (String term : scrub(query).split(" ")) {
      if (!indexed(term)) {
        continue;
      }
      String word = term.toLowerCase(Locale.ROOT);
      boolean head = isAsciiAlnum(word.charAt(0));
      boolean tail = isAsciiAlnum(word.charAt(word.length() - 1));
      if (head || tail) {
        pattern
            .append("(?=[\\s\\S]*")
            .append(head ? "(^|[^a-z0-9])" : "")
            .append(regexLiteral(word))
            .append(tail ? "([^a-z0-9]|$)" : "")
            .append(')');
      }
    }
    return pattern.isEmpty() ? null : "^" + pattern;
  }

  // 두 글자 영문·숫자(AI·Go)는 ngram이 email·google 속까지 맞추므로 색인 대신 낱말 폴백에 둔다.
  private static boolean indexed(String term) {
    if (term.length() < 2) return false;
    if (term.length() > 2) return true;
    String lower = term.toLowerCase(Locale.ROOT);
    return !(isAsciiAlnum(lower.charAt(0)) && isAsciiAlnum(lower.charAt(1)));
  }

  // 색인에 남는 항이 하나도 없는 질의만 제목·요약 LIKE 폴백을 사용한다.
  static String titleLikeFallback(String query) {
    String scrubbed = scrub(query);
    if (scrubbed.isEmpty()) {
      return null;
    }
    for (String term : scrubbed.split(" ")) {
      if (indexed(term)) {
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
      Long viewerId,
      Collection<Long> authorIds,
      Collection<Long> seriesIds,
      Collection<String> tags,
      int page,
      int size) {
    return followed(viewerId, authorIds, seriesIds, tags, PageRequest.of(page, size));
  }

  @Override
  public long countPublishedByAuthorsSeriesOrTags(
      Long viewerId,
      Collection<Long> authorIds,
      Collection<Long> seriesIds,
      Collection<String> tags) {
    if (seriesIds.isEmpty() && tags.isEmpty()) {
      return jpa.countPublishedByAuthors(
          idsForIn(authorIds), HeardSql.viewer(viewerId), Instant.now());
    }
    return jpa.countPublishedByAuthorsSeriesOrTags(
        idsForIn(authorIds),
        idsForIn(seriesIds),
        tagsForIn(tags),
        HeardSql.viewer(viewerId),
        Instant.now());
  }

  @Override
  public List<PostEntity> findForYouCandidates(
      Long userId, Collection<String> tags, Collection<Long> excludeIds, int page, int size) {
    return jpa.findForYouCandidates(
        userId,
        tagsForIn(tags),
        idsForIn(excludeIds),
        DiscoveryQuality.MIN_BODY_TEXT_LENGTH,
        Instant.now(),
        PageRequest.of(page, size));
  }

  @Override
  public long countForYouCandidates(
      Long userId, Collection<String> tags, Collection<Long> excludeIds) {
    return jpa.countForYouCandidates(
        userId,
        tagsForIn(tags),
        idsForIn(excludeIds),
        DiscoveryQuality.MIN_BODY_TEXT_LENGTH,
        Instant.now());
  }

  @Override
  public List<TagCount> findPopularTags(int limit) {
    return jpa
        .findPopularTags(DiscoveryQuality.MIN_BODY_TEXT_LENGTH, PageRequest.of(0, limit))
        .stream()
        .map(row -> new TagCount((String) row[0], ((Number) row[1]).longValue()))
        .toList();
  }

  @Override
  public List<AuthorPostStats> findTopAuthorStats(Long viewerId, int limit) {
    return jpa
        .findTopAuthorIds(
            DiscoveryQuality.MIN_BODY_TEXT_LENGTH,
            HeardSql.viewer(viewerId),
            Instant.now(),
            PageRequest.of(0, limit))
        .stream()
        .map(
            row ->
                new AuthorPostStats(
                    ((Number) row[0]).longValue(),
                    ((Number) row[1]).longValue(),
                    ((Number) row[2]).longValue()))
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
