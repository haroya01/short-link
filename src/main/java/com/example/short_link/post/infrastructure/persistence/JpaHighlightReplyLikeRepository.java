package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.HighlightReplyLikeEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaHighlightReplyLikeRepository
    extends JpaRepository<HighlightReplyLikeEntity, Long> {

  long countByHighlightReplyId(Long highlightReplyId);

  @Modifying
  @Query(
      value =
          "INSERT IGNORE INTO highlight_reply_like (highlight_reply_id, user_id, created_at) "
              + "VALUES (:replyId, :userId, NOW())",
      nativeQuery = true)
  int insertIgnore(@Param("replyId") Long replyId, @Param("userId") Long userId);

  @Modifying
  int deleteByHighlightReplyIdAndUserId(Long highlightReplyId, Long userId);

  @Query(
      value =
          "SELECT l.highlight_reply_id AS replyId, COUNT(*) AS cnt,"
              + " MAX(l.user_id = :viewer) AS mine FROM highlight_reply_like l"
              + " WHERE l.highlight_reply_id IN (:replyIds) GROUP BY l.highlight_reply_id",
      nativeQuery = true)
  List<ReplyLikeCount> countGroupedByReplyId(
      @Param("replyIds") Collection<Long> replyIds, @Param("viewer") long viewer);

  interface ReplyLikeCount {
    Long getReplyId();

    Long getCnt();

    Long getMine();
  }
}
