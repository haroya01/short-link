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

  // INSERT IGNORE avoids duplicate exceptions. Native inserts bypass @CreationTimestamp,
  // so created_at is set explicitly.
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

  @Modifying
  @Query("delete from PostLikeEntity p where p.postId = :postId")
  int deleteAllByPostId(@Param("postId") Long postId);
}
