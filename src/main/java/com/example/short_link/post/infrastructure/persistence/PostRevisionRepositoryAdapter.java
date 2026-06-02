package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.PostRevisionEntity;
import com.example.short_link.post.domain.repository.PostRevisionRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class PostRevisionRepositoryAdapter implements PostRevisionRepository {

  private final JpaPostRevisionRepository jpa;

  @Override
  public PostRevisionEntity save(PostRevisionEntity revision) {
    return jpa.save(revision);
  }

  @Override
  public Optional<PostRevisionEntity> findLatestByPostId(Long postId) {
    return jpa.findFirstByPostIdOrderByVersionNumberDesc(postId);
  }

  @Override
  public Optional<PostRevisionEntity> findByPostIdAndVersionNumber(
      Long postId, Integer versionNumber) {
    return jpa.findByPostIdAndVersionNumber(postId, versionNumber);
  }

  @Override
  public List<PostRevisionEntity> findAllByPostIdOrderByVersionNumberDesc(Long postId) {
    return jpa.findAllByPostIdOrderByVersionNumberDesc(postId);
  }

  @Override
  public void deleteAllByPostId(Long postId) {
    jpa.deleteAllByPostId(postId);
  }
}
