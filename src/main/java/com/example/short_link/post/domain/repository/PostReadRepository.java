package com.example.short_link.post.domain.repository;

import com.example.short_link.post.domain.PostReadEntity;
import java.util.List;
import java.util.Optional;

public interface PostReadRepository {

  Optional<PostReadEntity> findByUserIdAndPostId(Long userId, Long postId);

  PostReadEntity save(PostReadEntity read);

  List<PostReadEntity> findByUserIdOrderByReadAtDesc(Long userId, int page, int size);

  long countByUserId(Long userId);

  /** Returns the number of rows deleted. */
  int deleteByUserIdAndPostId(Long userId, Long postId);

  /** Returns the number of rows deleted. */
  int deleteByUserId(Long userId);

  int deleteAllByPostId(Long postId);
}
