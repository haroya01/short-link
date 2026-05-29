package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.CommentEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaCommentRepository extends JpaRepository<CommentEntity, Long> {

  List<CommentEntity> findAllByPostIdOrderByCreatedAtAsc(Long postId);

  List<CommentEntity> findAllByParentId(Long parentId);
}
