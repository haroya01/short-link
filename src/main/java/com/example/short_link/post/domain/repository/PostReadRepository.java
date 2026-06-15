package com.example.short_link.post.domain.repository;

import com.example.short_link.post.domain.PostReadEntity;
import java.util.List;
import java.util.Optional;

public interface PostReadRepository {

  Optional<PostReadEntity> findByUserIdAndPostId(Long userId, Long postId);

  PostReadEntity save(PostReadEntity read);

  /** A page of the user's reading history, most recently read first. */
  List<PostReadEntity> findByUserIdOrderByReadAtDesc(Long userId, int page, int size);

  /** How many distinct posts the user has read — drives {@code hasNext}. */
  long countByUserId(Long userId);

  /** Forget one entry (per-row remove from history). Returns rows deleted. */
  int deleteByUserIdAndPostId(Long userId, Long postId);

  /** Clear the user's whole reading history. Returns rows deleted. */
  int deleteByUserId(Long userId);

  /** Purge every read record on a post — used when the post is permanently deleted. */
  int deleteAllByPostId(Long postId);
}
