package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.PostHighlightEntity;
import com.example.short_link.post.domain.repository.PostHighlightRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class PostHighlightRepositoryAdapter implements PostHighlightRepository {

  private final JpaPostHighlightRepository jpa;

  @Override
  public PostHighlightEntity save(PostHighlightEntity highlight) {
    return jpa.save(highlight);
  }

  @Override
  public Optional<PostHighlightEntity> findById(Long id) {
    return jpa.findById(id);
  }

  @Override
  public void delete(PostHighlightEntity highlight) {
    jpa.delete(highlight);
  }

  @Override
  public List<PostHighlightEntity> findAllByPostIdOrderByBlockOrderAscStartOffsetAsc(Long postId) {
    return jpa.findAllByPostIdOrderByBlockOrderAscStartOffsetAsc(postId);
  }

  @Override
  public List<PostHighlightEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId) {
    return jpa.findAllByUserIdOrderByCreatedAtDesc(userId);
  }

  @Override
  public int deleteAllByPostId(Long postId) {
    return jpa.deleteAllByPostId(postId);
  }
}
