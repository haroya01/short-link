package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.PostRevisionEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaPostRevisionRepository extends JpaRepository<PostRevisionEntity, Long> {

  List<PostRevisionEntity> findAllByPostIdOrderByVersionNumberDesc(Long postId);

  Optional<PostRevisionEntity> findFirstByPostIdOrderByVersionNumberDesc(Long postId);

  @Modifying
  @Query("delete from PostRevisionEntity r where r.postId = :postId")
  void deleteAllByPostId(@Param("postId") Long postId);
}
