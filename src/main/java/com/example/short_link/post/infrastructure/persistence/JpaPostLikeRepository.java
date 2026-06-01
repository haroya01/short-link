package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.PostLikeEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaPostLikeRepository extends JpaRepository<PostLikeEntity, Long> {

  boolean existsByPostIdAndUserId(Long postId, Long userId);

  Optional<PostLikeEntity> findByPostIdAndUserId(Long postId, Long userId);

  List<PostLikeEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId);
}
