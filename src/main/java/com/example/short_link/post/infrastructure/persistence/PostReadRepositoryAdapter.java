package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.PostReadEntity;
import com.example.short_link.post.domain.repository.PostReadRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class PostReadRepositoryAdapter implements PostReadRepository {

  private final JpaPostReadRepository jpa;

  @Override
  public Optional<PostReadEntity> findByUserIdAndPostId(Long userId, Long postId) {
    return jpa.findByUserIdAndPostId(userId, postId);
  }

  @Override
  public PostReadEntity save(PostReadEntity read) {
    return jpa.save(read);
  }

  @Override
  public List<PostReadEntity> findByUserIdOrderByReadAtDesc(Long userId, int page, int size) {
    return jpa.findPage(userId, PageRequest.of(page, size));
  }

  @Override
  public long countByUserId(Long userId) {
    return jpa.countByUserId(userId);
  }

  @Override
  public int deleteByUserIdAndPostId(Long userId, Long postId) {
    return jpa.deleteByUserIdAndPostId(userId, postId);
  }

  @Override
  public int deleteByUserId(Long userId) {
    return jpa.deleteByUserId(userId);
  }

  @Override
  public int deleteAllByPostId(Long postId) {
    return jpa.deleteAllByPostId(postId);
  }
}
