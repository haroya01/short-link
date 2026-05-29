package com.example.short_link.post.domain.repository;

import com.example.short_link.post.domain.CommentEntity;
import java.util.List;
import java.util.Optional;

public interface CommentRepository {

  CommentEntity save(CommentEntity comment);

  Optional<CommentEntity> findById(Long id);

  void delete(CommentEntity comment);

  /** All comments for a post (top-level + replies), oldest first. */
  List<CommentEntity> findAllByPostIdOrderByCreatedAtAsc(Long postId);

  /** Replies to a top-level comment — used to cascade on delete. */
  List<CommentEntity> findAllByParentId(Long parentId);
}
