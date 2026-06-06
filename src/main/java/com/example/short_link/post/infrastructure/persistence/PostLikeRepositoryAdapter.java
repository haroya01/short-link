package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.PostLikeEntity;
import com.example.short_link.post.domain.repository.PostLikeRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class PostLikeRepositoryAdapter implements PostLikeRepository {

  private final JpaPostLikeRepository jpa;

  @Override
  public boolean existsByPostIdAndUserId(Long postId, Long userId) {
    return jpa.existsByPostIdAndUserId(postId, userId);
  }

  @Override
  public List<PostLikeEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId) {
    return jpa.findAllByUserIdOrderByCreatedAtDesc(userId);
  }

  @Override
  public long countByPostId(Long postId) {
    return jpa.countByPostId(postId);
  }

  @Override
  public int insertIgnore(Long postId, Long userId) {
    return jpa.insertIgnore(postId, userId);
  }

  @Override
  public int deleteByPostIdAndUserId(Long postId, Long userId) {
    return jpa.deleteByPostIdAndUserId(postId, userId);
  }

  @Override
  public int deleteAllByPostId(Long postId) {
    return jpa.deleteAllByPostId(postId);
  }
}
