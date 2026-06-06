package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.CommentEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaCommentRepository extends JpaRepository<CommentEntity, Long> {

  List<CommentEntity> findAllByPostIdOrderByCreatedAtAsc(Long postId);

  List<CommentEntity> findAllByParentId(Long parentId);

  @Modifying
  @Query("delete from CommentEntity c where c.postId = :postId")
  int deleteAllByPostId(@Param("postId") Long postId);
}
