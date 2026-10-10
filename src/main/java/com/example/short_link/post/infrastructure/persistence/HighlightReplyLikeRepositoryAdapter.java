package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.common.user.HeardSql;
import com.example.short_link.post.domain.repository.HighlightReplyLikeRepository;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class HighlightReplyLikeRepositoryAdapter implements HighlightReplyLikeRepository {

  private final JpaHighlightReplyLikeRepository jpa;

  @Override
  public int insertIgnore(Long replyId, Long userId) {
    return jpa.insertIgnore(replyId, userId);
  }

  @Override
  public int deleteByReplyIdAndUserId(Long replyId, Long userId) {
    return jpa.deleteByHighlightReplyIdAndUserId(replyId, userId);
  }

  @Override
  public long countByReplyId(Long replyId) {
    return jpa.countByHighlightReplyId(replyId);
  }

  @Override
  public Map<Long, Likes> likesOf(Collection<Long> replyIds, Long viewerId) {
    if (replyIds.isEmpty()) return Map.of();
    return jpa.countGroupedByReplyId(replyIds, HeardSql.viewer(viewerId)).stream()
        .collect(
            Collectors.toMap(
                JpaHighlightReplyLikeRepository.ReplyLikeCount::getReplyId,
                row -> new Likes(row.getCnt(), row.getMine() != null && row.getMine() > 0)));
  }
}
