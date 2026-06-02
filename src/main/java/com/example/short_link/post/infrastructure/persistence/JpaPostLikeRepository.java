package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.PostLikeEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaPostLikeRepository extends JpaRepository<PostLikeEntity, Long> {

  boolean existsByPostIdAndUserId(Long postId, Long userId);

  List<PostLikeEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId);

  long countByPostId(Long postId);

  // MySQL INSERT IGNORE: the (post_id, user_id) unique key turns a duplicate like into a no-op
  // (0 rows) instead of a constraint violation, so the like flow stays idempotent without catching
  // an exception inside the transaction. created_at is set here because the native insert bypasses
  // the @CreationTimestamp callback.
  @Modifying
  @Query(
      value =
          "INSERT IGNORE INTO post_like (post_id, user_id, created_at) "
              + "VALUES (:postId, :userId, NOW())",
      nativeQuery = true)
  int insertIgnore(@Param("postId") Long postId, @Param("userId") Long userId);

  @Modifying
  @Query("delete from PostLikeEntity p where p.postId = :postId and p.userId = :userId")
  int deleteByPostIdAndUserId(@Param("postId") Long postId, @Param("userId") Long userId);
}
