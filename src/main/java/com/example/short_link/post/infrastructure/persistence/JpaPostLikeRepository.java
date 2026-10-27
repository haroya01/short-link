package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.PostLikeEntity;
import com.example.short_link.post.domain.repository.PostLikeRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaPostLikeRepository
    extends JpaRepository<PostLikeEntity, Long>, PostLikeRepository {

  // INSERT IGNORE avoids duplicate exceptions. Native inserts bypass @CreationTimestamp,
  // so created_at is set explicitly.
  @Override
  @Modifying
  @Query(
      value =
          "INSERT IGNORE INTO post_like (post_id, user_id, created_at) "
              + "VALUES (:postId, :userId, UTC_TIMESTAMP(6))",
      nativeQuery = true)
  int insertIgnore(@Param("postId") Long postId, @Param("userId") Long userId);

  @Override
  @Modifying
  @Query("delete from PostLikeEntity p where p.postId = :postId and p.userId = :userId")
  int deleteByPostIdAndUserId(@Param("postId") Long postId, @Param("userId") Long userId);

  @Override
  @Modifying
  @Query("delete from PostLikeEntity p where p.postId = :postId")
  int deleteAllByPostId(@Param("postId") Long postId);
}
