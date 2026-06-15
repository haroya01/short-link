package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.PostReadEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaPostReadRepository extends JpaRepository<PostReadEntity, Long> {

  Optional<PostReadEntity> findByUserIdAndPostId(Long userId, Long postId);

  @Query(
      "select r from PostReadEntity r where r.userId = :userId order by r.readAt desc, r.id desc")
  List<PostReadEntity> findPage(@Param("userId") Long userId, Pageable pageable);

  long countByUserId(Long userId);

  @Modifying
  @Query("delete from PostReadEntity r where r.userId = :userId and r.postId = :postId")
  int deleteByUserIdAndPostId(@Param("userId") Long userId, @Param("postId") Long postId);

  @Modifying
  @Query("delete from PostReadEntity r where r.userId = :userId")
  int deleteByUserId(@Param("userId") Long userId);

  @Modifying
  @Query("delete from PostReadEntity r where r.postId = :postId")
  int deleteAllByPostId(@Param("postId") Long postId);
}
