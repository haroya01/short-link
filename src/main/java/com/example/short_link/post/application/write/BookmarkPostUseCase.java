package com.example.short_link.post.application.write;

import com.example.short_link.post.application.read.PostBookmarkStatus;
import com.example.short_link.post.domain.repository.PostBookmarkRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Add / remove a post from the caller's reading list. Idempotent. */
@Service
@RequiredArgsConstructor
public class BookmarkPostUseCase {

  private final PostRepository postRepository;
  private final PostBookmarkRepository postBookmarkRepository;

  @Transactional
  public PostBookmarkStatus bookmark(Long userId, Long postId) {
    requirePost(postId);
    // INSERT IGNORE: idempotent + race-safe — a concurrent duplicate becomes a no-op instead of a
    // unique-key violation.
    postBookmarkRepository.insertIgnore(postId, userId);
    return new PostBookmarkStatus(true);
  }

  @Transactional
  public PostBookmarkStatus unbookmark(Long userId, Long postId) {
    postBookmarkRepository
        .findByPostIdAndUserId(postId, userId)
        .ifPresent(postBookmarkRepository::delete);
    return new PostBookmarkStatus(false);
  }

  private void requirePost(Long postId) {
    if (!postRepository.findById(postId).map(p -> true).orElse(false)) {
      throw new PostException(PostErrorCode.POST_NOT_FOUND, postId);
    }
  }
}
