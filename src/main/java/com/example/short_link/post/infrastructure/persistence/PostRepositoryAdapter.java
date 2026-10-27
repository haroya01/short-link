package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.common.note.NoteAccessSql;
import com.example.short_link.common.post.PostAccessSql;
import com.example.short_link.common.seen.Unseen;
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
import jakarta.persistence.Query;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.hibernate.query.NativeQuery;
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
  private final Clock clock;

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
        clock.instant(),
        PageRequest.of(page, size));
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<PostEntity> findHomeRecentAt(Long viewerId, int offset, int limit, Unseen unseen) {
    String shown =
        "("
            + JpaPostRepository.BROWSE_CONDITION
            + ") OR ("
            + PostAccessSql.inFeedRooms("p")
            + HeardSql.POST_AUTHOR
            + ")";
    Query query =
        em.createNativeQuery(
            "SELECT p.* FROM posts p WHERE "
                + (unseen == null ? shown : "(" + shown + ")" + Unseen.sql("p", Unseen.POST))
                + " ORDER BY p.published_at DESC, p.id DESC",
            PostEntity.class);
    if (unseen != null) {
      unseen.bind(query, Unseen.POST);
    }
    return query
        .setParameter("lang", null)
        .setParameter("minBody", DiscoveryQuality.MIN_BODY_TEXT_LENGTH)
        .setParameter("viewer", HeardSql.viewer(viewerId))
        .setParameter("now", clock.instant())
        .setFirstResult(offset)
        .setMaxResults(limit)
        .getResultList();
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<PoolRow> findHomePool(Long viewerId, Instant since, Instant until, int limit) {
    List<Object[]> rows =
        em.createNativeQuery(
                "SELECT p.id, p.user_id, p.language_tag, p.published_at, p.like_count,"
                    + " (SELECT COUNT(*) FROM note q WHERE q.quoted_post_id = p.id AND "
                    + NoteAccessSql.shareable("q")
                    + ") AS quotes,"
                    + " (SELECT COUNT(*) FROM post_highlight h WHERE h.post_id = p.id) AS highlights"
                    + " FROM posts p WHERE "
                    + JpaPostRepository.BROWSE_CONDITION
                    + " AND p.published_at >= :since AND p.published_at <= :until"
                    + " ORDER BY p.published_at DESC, p.id DESC")
            .setParameter("lang", null)
            .setParameter("minBody", DiscoveryQuality.MIN_BODY_TEXT_LENGTH)
            .setParameter("since", since)
            .setParameter("until", until)
            .setParameter("viewer", HeardSql.viewer(viewerId))
            .setParameter("now", clock.instant())
            .setMaxResults(limit)
            .unwrap(NativeQuery.class)
            .addScalar("id", Long.class)
            .addScalar("user_id", Long.class)
            .addScalar("language_tag", String.class)
            .addScalar("published_at", Instant.class)
            .addScalar("like_count", Long.class)
            .addScalar("quotes", Long.class)
            .addScalar("highlights", Long.class)
            .getResultList();
    if (rows.isEmpty()) {
      return List.of();
    }
    Map<Long, List<String>> tags = new HashMap<>();
    List<Object[]> tagRows =
        em.createNativeQuery("SELECT post_id, tag FROM post_tag WHERE post_id IN (:ids)")
            .setParameter("ids", rows.stream().map(row -> (Long) row[0]).toList())
            .unwrap(NativeQuery.class)
            .addScalar("post_id", Long.class)
            .addScalar("tag", String.class)
            .getResultList();
    tagRows.forEach(
        row -> tags.computeIfAbsent((Long) row[0], id -> new ArrayList<>()).add((String) row[1]));
    return rows.stream()
        .map(
            row ->
                new PoolRow(
                    (Long) row[0],
                    (Long) row[1],
                    (String) row[2],
                    (Instant) row[3],
                    (Long) row[4],
                    (Long) row[5],
                    (Long) row[6],
                    tags.getOrDefault((Long) row[0], List.of())))
        .toList();
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<PostEntity> findBlogBelow(Long viewerId, Instant publishedAt, Long id, int limit) {
    Query query =
        em.createNativeQuery(
            "SELECT p.* FROM posts p WHERE "
                + JpaPostRepository.BROWSE_CONDITION
                + (publishedAt == null
                    ? ""
                    : " AND (p.published_at < :at OR (p.published_at = :at AND p.id < :id))")
                + " ORDER BY p.published_at DESC, p.id DESC",
            PostEntity.class);
    if (publishedAt != null) {
      query.setParameter("at", publishedAt).setParameter("id", id);
    }
    return blog(query, viewerId, limit);
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<PostEntity> findBlogAbove(Long viewerId, Instant publishedAt, long id, int limit) {
    Query query =
        em.createNativeQuery(
            "SELECT p.* FROM posts p WHERE "
                + JpaPostRepository.BROWSE_CONDITION
                + " AND (p.published_at > :at OR (p.published_at = :at AND p.id > :id))"
                + " ORDER BY p.published_at, p.id",
            PostEntity.class);
    query.setParameter("at", publishedAt).setParameter("id", id);
    return blog(query, viewerId, limit);
  }

  @SuppressWarnings("unchecked")
  private List<PostEntity> blog(Query query, Long viewerId, int limit) {
    return query
        .setParameter("lang", null)
        .setParameter("minBody", DiscoveryQuality.MIN_BODY_TEXT_LENGTH)
        .setParameter("viewer", HeardSql.viewer(viewerId))
        .setParameter("now", clock.instant())
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
        .setParameter("now", clock.instant())
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
        .setParameter("now", clock.instant())
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
    Instant now = clock.instant();
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
                    + " ORDER BY p.published_at DESC, p.id DESC",
                PostEntity.class)
            .setParameter("authorIds", idsForIn(authorIds))
            .setParameter("viewer", HeardSql.viewer(viewerId))
            .setParameter("now", clock.instant());
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
          idsForIn(authorIds), HeardSql.viewer(viewerId), clock.instant(), pageable);
    }
    return jpa.findPublishedByAuthorsSeriesOrTags(
        idsForIn(authorIds),
        idsForIn(seriesIds),
        tagsForIn(tags),
        HeardSql.viewer(viewerId),
        clock.instant(),
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
        clock.instant());
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
        clock.instant(),
        PageRequest.of(page, size));
  }

  @Override
  public List<PostEntity> findPublishedTrendingByTag(
      Long viewerId, String tag, int page, int size) {
    Instant now = clock.instant();
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
        noteId, HeardSql.viewer(viewerId), clock.instant(), offset, limit);
  }

  @Override
  public long countPublishedByTag(Long viewerId, String tag) {
    return jpa.countPublishedByTag(
        tag, DiscoveryQuality.MIN_BODY_TEXT_LENGTH, HeardSql.viewer(viewerId), clock.instant());
  }

  @Override
  public List<PostEntity> searchPublishedByRelevanceAt(
      Long viewerId, String query, String lang, long offset, int limit) {
    return jpa.searchPublishedByRelevance(
        PostSearchTerms.booleanMatch(query),
        PostSearchTerms.matchWordBoundaries(query),
        PostSearchTerms.likePattern(query),
        PostSearchTerms.titleLikeFallback(query),
        PostSearchTerms.titleWordFallback(query),
        normLang(lang),
        HeardSql.viewer(viewerId),
        clock.instant(),
        new OffsetLimit(offset, limit));
  }

  @Override
  public List<PostEntity> searchPublishedAt(
      Long viewerId, String query, String lang, long offset, int limit) {
    return jpa.searchPublishedRecent(
        PostSearchTerms.booleanMatch(query),
        PostSearchTerms.matchWordBoundaries(query),
        PostSearchTerms.likePattern(query),
        PostSearchTerms.titleLikeFallback(query),
        PostSearchTerms.titleWordFallback(query),
        normLang(lang),
        HeardSql.viewer(viewerId),
        clock.instant(),
        new OffsetLimit(offset, limit));
  }

  @Override
  public List<PostEntity> searchPublishedTrendingAt(
      Long viewerId, String query, String lang, long offset, int limit) {
    Instant now = clock.instant();
    return jpa.searchPublishedTrendingSince(
        PostSearchTerms.booleanMatch(query),
        PostSearchTerms.matchWordBoundaries(query),
        PostSearchTerms.likePattern(query),
        PostSearchTerms.titleLikeFallback(query),
        PostSearchTerms.titleWordFallback(query),
        now.minus(TRENDING_WINDOW),
        normLang(lang),
        HeardSql.viewer(viewerId),
        now,
        new OffsetLimit(offset, limit));
  }

  private static String normLang(String lang) {
    return lang == null || lang.isBlank() ? null : lang.trim();
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
          idsForIn(authorIds), HeardSql.viewer(viewerId), clock.instant());
    }
    return jpa.countPublishedByAuthorsSeriesOrTags(
        idsForIn(authorIds),
        idsForIn(seriesIds),
        tagsForIn(tags),
        HeardSql.viewer(viewerId),
        clock.instant());
  }

  @Override
  public List<PostEntity> findForYouCandidates(
      Long userId, Collection<String> tags, Collection<Long> excludeIds, int page, int size) {
    return jpa.findForYouCandidates(
        userId,
        tagsForIn(tags),
        idsForIn(excludeIds),
        DiscoveryQuality.MIN_BODY_TEXT_LENGTH,
        clock.instant(),
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
        clock.instant());
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
            clock.instant(),
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
