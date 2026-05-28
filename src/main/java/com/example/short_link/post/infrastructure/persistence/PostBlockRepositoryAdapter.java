package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.PostBlockEntity;
import com.example.short_link.post.domain.repository.PostBlockRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class PostBlockRepositoryAdapter implements PostBlockRepository {

  private final JpaPostBlockRepository jpa;

  @Override
  public List<PostBlockEntity> saveAll(List<PostBlockEntity> blocks) {
    return jpa.saveAll(blocks);
  }

  @Override
  public List<PostBlockEntity> findAllByPostIdOrderByBlockOrderAsc(Long postId) {
    return jpa.findAllByPostIdOrderByBlockOrderAsc(postId);
  }

  @Override
  public void deleteAllByPostId(Long postId) {
    jpa.deleteAllByPostId(postId);
  }
}
