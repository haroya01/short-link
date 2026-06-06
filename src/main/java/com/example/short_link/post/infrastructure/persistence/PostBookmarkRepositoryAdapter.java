package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.FolderBookmarkCount;
import com.example.short_link.post.domain.PostBookmarkEntity;
import com.example.short_link.post.domain.repository.PostBookmarkRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class PostBookmarkRepositoryAdapter implements PostBookmarkRepository {

  private final JpaPostBookmarkRepository jpa;

  @Override
  public boolean existsByPostIdAndUserId(Long postId, Long userId) {
    return jpa.existsByPostIdAndUserId(postId, userId);
  }

  @Override
  public Optional<PostBookmarkEntity> findByPostIdAndUserId(Long postId, Long userId) {
    return jpa.findByPostIdAndUserId(postId, userId);
  }

  @Override
  public List<PostBookmarkEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId) {
    return jpa.findAllByUserIdOrderByCreatedAtDesc(userId);
  }

  @Override
  public List<FolderBookmarkCount> countByFolder(Long userId) {
    return jpa.countByFolder(userId);
  }

  @Override
  public PostBookmarkEntity save(PostBookmarkEntity bookmark) {
    return jpa.save(bookmark);
  }

  @Override
  public void delete(PostBookmarkEntity bookmark) {
    jpa.delete(bookmark);
  }
}
