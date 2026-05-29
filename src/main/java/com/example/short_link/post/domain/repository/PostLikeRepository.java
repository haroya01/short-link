package com.example.short_link.post.domain.repository;

import com.example.short_link.post.domain.PostLikeEntity;
import java.util.Optional;

public interface PostLikeRepository {

  boolean existsByPostIdAndUserId(Long postId, Long userId);

  Optional<PostLikeEntity> findByPostIdAndUserId(Long postId, Long userId);

  PostLikeEntity save(PostLikeEntity like);

  void delete(PostLikeEntity like);
}
