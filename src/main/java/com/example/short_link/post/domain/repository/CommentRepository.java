package com.example.short_link.post.domain.repository;

import com.example.short_link.post.domain.CommentEntity;
import java.util.List;
import java.util.Optional;

public interface CommentRepository {

  CommentEntity save(CommentEntity comment);

  Optional<CommentEntity> findById(Long id);

  void delete(CommentEntity comment);

  List<CommentEntity> findAllByPostIdOrderByCreatedAtAsc(Long postId);

  List<CommentEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId);

  List<CommentEntity> findAllByParentId(Long parentId);

  int deleteAllByPostId(Long postId);
}
