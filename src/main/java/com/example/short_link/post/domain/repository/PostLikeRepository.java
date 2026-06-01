package com.example.short_link.post.domain.repository;

import com.example.short_link.post.domain.PostLikeEntity;
import java.util.List;
import java.util.Optional;

public interface PostLikeRepository {

  boolean existsByPostIdAndUserId(Long postId, Long userId);

  Optional<PostLikeEntity> findByPostIdAndUserId(Long postId, Long userId);

  /** The user's likes, newest first — drives the "liked posts" list. */
  List<PostLikeEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId);

  PostLikeEntity save(PostLikeEntity like);

  void delete(PostLikeEntity like);
}
