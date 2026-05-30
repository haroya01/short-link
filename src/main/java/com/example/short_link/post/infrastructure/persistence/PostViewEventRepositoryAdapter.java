package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.PostViewEventEntity;
import com.example.short_link.post.domain.repository.PostViewEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class PostViewEventRepositoryAdapter implements PostViewEventRepository {

  private final JpaPostViewEventRepository jpa;

  @Override
  public PostViewEventEntity save(PostViewEventEntity event) {
    return jpa.save(event);
  }
}
