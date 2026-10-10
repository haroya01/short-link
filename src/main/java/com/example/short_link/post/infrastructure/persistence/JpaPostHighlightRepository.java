package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.PostHighlightEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaPostHighlightRepository extends JpaRepository<PostHighlightEntity, Long> {

  List<PostHighlightEntity> findAllByIdIn(Collection<Long> ids);

  List<PostHighlightEntity> findAllByPostIdOrderByBlockOrderAscStartOffsetAsc(Long postId);

  List<PostHighlightEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId);

  @Modifying
  @Query("delete from PostHighlightEntity h where h.postId = :postId")
  int deleteAllByPostId(@Param("postId") Long postId);
}
