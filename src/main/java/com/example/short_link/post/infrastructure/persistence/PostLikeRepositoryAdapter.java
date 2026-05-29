package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.PostLikeEntity;
import com.example.short_link.post.domain.repository.PostLikeRepository;
import java.util.Optional;
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
  public Optional<PostLikeEntity> findByPostIdAndUserId(Long postId, Long userId) {
    return jpa.findByPostIdAndUserId(postId, userId);
  }

  @Override
  public PostLikeEntity save(PostLikeEntity like) {
    return jpa.save(like);
  }

  @Override
  public void delete(PostLikeEntity like) {
    jpa.delete(like);
  }
}
