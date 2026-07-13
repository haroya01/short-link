package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.CommentEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaCommentRepository extends JpaRepository<CommentEntity, Long> {

  // 공개 목록은 관리자 soft 삭제(deleted_at) 된 댓글을 제외한다. (본인 물리삭제는 행이 사라지므로 자동 제외.)
  List<CommentEntity> findAllByPostIdAndDeletedAtIsNullOrderByCreatedAtAsc(Long postId);

  List<CommentEntity> findAllByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long userId);

  List<CommentEntity> findAllByParentId(Long parentId);

  @Modifying
  @Query("delete from CommentEntity c where c.postId = :postId")
  int deleteAllByPostId(@Param("postId") Long postId);
}
