package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostStatus;
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
}
