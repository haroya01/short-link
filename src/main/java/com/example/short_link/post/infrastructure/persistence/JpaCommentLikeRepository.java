package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.CommentLikeEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaCommentLikeRepository extends JpaRepository<CommentLikeEntity, Long> {

  long countByCommentId(Long commentId);

  // MySQL INSERT IGNORE — (comment_id, user_id) 유니크 키가 중복 좋아요를 0-row no-op 으로 만들어
  // 트랜잭션 안에서 예외 없이 멱등(post_like 와 동일 계약). created_at 은 네이티브라 직접 채운다.
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
