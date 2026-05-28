package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.PostBlockEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaPostBlockRepository extends JpaRepository<PostBlockEntity, Long> {

  List<PostBlockEntity> findAllByPostIdOrderByBlockOrderAsc(Long postId);

  @Modifying
  @Query("delete from PostBlockEntity b where b.postId = :postId")
  void deleteAllByPostId(@Param("postId") Long postId);
}
