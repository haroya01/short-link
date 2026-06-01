package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.PostBookmarkEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaPostBookmarkRepository extends JpaRepository<PostBookmarkEntity, Long> {

  boolean existsByPostIdAndUserId(Long postId, Long userId);

  Optional<PostBookmarkEntity> findByPostIdAndUserId(Long postId, Long userId);

  List<PostBookmarkEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId);
}
