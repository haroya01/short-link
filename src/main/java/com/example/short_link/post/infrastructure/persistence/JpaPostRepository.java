package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaPostRepository extends JpaRepository<PostEntity, Long> {

  Optional<PostEntity> findByUserIdAndSlug(Long userId, String slug);

  boolean existsByUserIdAndSlug(Long userId, String slug);

  List<PostEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId);

  List<PostEntity> findAllByUserIdAndStatusOrderByPublishedAtDesc(Long userId, PostStatus status);

  List<PostEntity> findAllBySeriesIdOrderBySeriesOrderAsc(Long seriesId);

  List<PostEntity> findAllBySeriesIdAndStatusOrderBySeriesOrderAsc(
      Long seriesId, PostStatus status);

  List<PostEntity> findByStatusOrderByPublishedAtDesc(PostStatus status, Pageable pageable);

  List<PostEntity> findByStatusOrderByViewCountDescPublishedAtDesc(
      PostStatus status, Pageable pageable);

  long countByStatus(PostStatus status);

  @Query(
      "select p from PostEntity p join p.tags t "
          + "where lower(t) = lower(:tag) and p.status = :status "
          + "order by p.publishedAt desc")
  List<PostEntity> findPublishedByTag(
      @Param("tag") String tag, @Param("status") PostStatus status, Pageable pageable);

  @Query(
      "select count(p) from PostEntity p join p.tags t "
          + "where lower(t) = lower(:tag) and p.status = :status")
  long countPublishedByTag(@Param("tag") String tag, @Param("status") PostStatus status);

  @Query(
      "select p from PostEntity p "
          + "where p.userId in :ids and p.status = :status "
          + "order by p.publishedAt desc")
  List<PostEntity> findPublishedByAuthorIds(
      @Param("ids") Collection<Long> ids, @Param("status") PostStatus status, Pageable pageable);

  @Query("select count(p) from PostEntity p where p.userId in :ids and p.status = :status")
  long countPublishedByAuthorIds(
      @Param("ids") Collection<Long> ids, @Param("status") PostStatus status);

  @Query(
      "select t, count(p) from PostEntity p join p.tags t "
          + "where p.status = :status group by t order by count(p) desc")
  List<Object[]> findPopularTags(@Param("status") PostStatus status, Pageable pageable);

  // Free-text feed search. Matches title / excerpt / any tag / author handle, newest first. `q` is
  // a
  // pre-lowercased, wildcard-escaped LIKE pattern (already wrapped in %…%) built by the adapter, so
  // the query only LIKEs it. The author match is a subquery against UserEntity because a post holds
  // a
  // raw userId, not an association — deleted authors are excluded there. `distinct` collapses the
  // row
  // fan-out from the tag join.
  @Query(
      "select distinct p from PostEntity p left join p.tags t "
          + "where p.status = :status and ("
          + "lower(p.title) like :q escape '!' "
          + "or lower(p.excerpt) like :q escape '!' "
          + "or lower(t) like :q escape '!' "
          + "or p.userId in (select u.id from UserEntity u "
          + "where lower(u.username) like :q escape '!' and u.deletedAt is null)) "
          + "order by p.publishedAt desc")
  List<PostEntity> searchPublished(
      @Param("q") String q, @Param("status") PostStatus status, Pageable pageable);

  // Same match as searchPublished, ranked by view count (newest as tiebreak) for the trending sort.
  @Query(
      "select distinct p from PostEntity p left join p.tags t "
          + "where p.status = :status and ("
          + "lower(p.title) like :q escape '!' "
          + "or lower(p.excerpt) like :q escape '!' "
          + "or lower(t) like :q escape '!' "
          + "or p.userId in (select u.id from UserEntity u "
          + "where lower(u.username) like :q escape '!' and u.deletedAt is null)) "
          + "order by p.viewCount desc, p.publishedAt desc")
  List<PostEntity> searchPublishedTrending(
      @Param("q") String q, @Param("status") PostStatus status, Pageable pageable);

  @Query(
      "select count(distinct p) from PostEntity p left join p.tags t "
          + "where p.status = :status and ("
          + "lower(p.title) like :q escape '!' "
          + "or lower(p.excerpt) like :q escape '!' "
          + "or lower(t) like :q escape '!' "
          + "or p.userId in (select u.id from UserEntity u "
          + "where lower(u.username) like :q escape '!' and u.deletedAt is null))")
  long countSearchPublished(@Param("q") String q, @Param("status") PostStatus status);

  // Authors ranked for the discovery rail — most published posts first, total views as tiebreak.
  // Returns [userId, postCount, totalViews]; the service hydrates authors and drops deleted ones.
  @Query(
      "select p.userId, count(p), coalesce(sum(p.viewCount), 0) from PostEntity p "
          + "where p.status = :status group by p.userId "
          + "order by count(p) desc, coalesce(sum(p.viewCount), 0) desc")
  List<Object[]> findTopAuthorIds(@Param("status") PostStatus status, Pageable pageable);
}
