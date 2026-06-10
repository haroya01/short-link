package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.repository.CommentLikeRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CommentLikeRepositoryAdapter implements CommentLikeRepository {

  private final JpaCommentLikeRepository jpa;

  @Override
  public int insertIgnore(Long commentId, Long userId) {
    return jpa.insertIgnore(commentId, userId);
  }

  @Override
  public int deleteByCommentIdAndUserId(Long commentId, Long userId) {
    return jpa.deleteByCommentIdAndUserId(commentId, userId);
  }

  @Override
  public long countByCommentId(Long commentId) {
    return jpa.countByCommentId(commentId);
  }

  @Override
  public Map<Long, Long> countByCommentIds(List<Long> commentIds) {
    if (commentIds.isEmpty()) return Map.of();
    return jpa.countGroupedByCommentId(commentIds).stream()
        .collect(
            Collectors.toMap(
                JpaCommentLikeRepository.CommentLikeCount::getCommentId,
                JpaCommentLikeRepository.CommentLikeCount::getCnt));
  }

  @Override
  public List<Long> findLikedCommentIds(Long userId, List<Long> commentIds) {
    if (commentIds.isEmpty()) return List.of();
    return jpa.findLikedCommentIds(userId, commentIds);
  }
}
