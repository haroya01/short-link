package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.CommentEntity;
import com.example.short_link.post.domain.repository.CommentRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class CommentRepositoryAdapter implements CommentRepository {

  private final JpaCommentRepository jpa;

  @Override
  public CommentEntity save(CommentEntity comment) {
    return jpa.save(comment);
  }

  @Override
  public Optional<CommentEntity> findById(Long id) {
    return jpa.findById(id);
  }

  @Override
  public void delete(CommentEntity comment) {
    jpa.delete(comment);
  }

  @Override
  public List<CommentEntity> findAllByPostIdOrderByCreatedAtAsc(Long postId) {
    return jpa.findAllByPostIdOrderByCreatedAtAsc(postId);
  }

  @Override
  public List<CommentEntity> findAllByParentId(Long parentId) {
    return jpa.findAllByParentId(parentId);
  }
}
