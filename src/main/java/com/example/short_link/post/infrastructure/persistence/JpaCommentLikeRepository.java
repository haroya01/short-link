package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.CommentLikeEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaCommentLikeRepository extends JpaRepository<CommentLikeEntity, Long> {

  long countByCommentId(Long commentId);

  // INSERT IGNORE keeps duplicates from failing the transaction; native inserts set created_at
  // explicitly.
  @Modifying
  @Query(
      value =
          "INSERT IGNORE INTO comment_like (comment_id, user_id, created_at) "
              + "VALUES (:commentId, :userId, NOW())",
      nativeQuery = true)
  int insertIgnore(@Param("commentId") Long commentId, @Param("userId") Long userId);

  @Modifying
  int deleteByCommentIdAndUserId(Long commentId, Long userId);

  @Query(
      "SELECT cl.commentId AS commentId, COUNT(cl) AS cnt FROM CommentLikeEntity cl "
          + "WHERE cl.commentId IN :commentIds GROUP BY cl.commentId")
  List<CommentLikeCount> countGroupedByCommentId(@Param("commentIds") List<Long> commentIds);

  @Query(
      "SELECT cl.commentId FROM CommentLikeEntity cl "
          + "WHERE cl.userId = :userId AND cl.commentId IN :commentIds")
  List<Long> findLikedCommentIds(
      @Param("userId") Long userId, @Param("commentIds") List<Long> commentIds);

  interface CommentLikeCount {
    Long getCommentId();

    Long getCnt();
  }
}
