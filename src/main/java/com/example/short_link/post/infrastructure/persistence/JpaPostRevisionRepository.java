package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.PostRevisionEntity;
import com.example.short_link.post.domain.repository.PostRevisionRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaPostRevisionRepository
    extends JpaRepository<PostRevisionEntity, Long>, PostRevisionRepository {

  @Override
  @Modifying
  @Query("delete from PostRevisionEntity r where r.postId = :postId")
  void deleteAllByPostId(@Param("postId") Long postId);
}
