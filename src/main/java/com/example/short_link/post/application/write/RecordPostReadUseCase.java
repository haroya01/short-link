package com.example.short_link.post.application.write;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostReadEntity;
import com.example.short_link.post.domain.repository.PostReadRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records / clears the caller's reading history. Recording is a beacon — only published posts
 * count, a re-read just floats the entry to the top ({@code touch}), and a read of a missing /
 * unpublished post is silently ignored so it never disrupts the reader.
 */
@Service
@RequiredArgsConstructor
public class RecordPostReadUseCase {

  private final PostRepository postRepository;
  private final PostReadRepository postReadRepository;

  @Transactional
  public void record(Long userId, Long postId) {
    if (postRepository.findById(postId).filter(PostEntity::isPublished).isEmpty()) {
      return;
    }
    Instant now = Instant.now();
    postReadRepository
        .findByUserIdAndPostId(userId, postId)
        .ifPresentOrElse(
            existing -> {
              existing.touch(now);
              postReadRepository.save(existing);
            },
            () -> postReadRepository.save(new PostReadEntity(userId, postId, now)));
  }

  /** Forget one post from the history. */
  @Transactional
  public void remove(Long userId, Long postId) {
    postReadRepository.deleteByUserIdAndPostId(userId, postId);
  }

  /** Clear the whole reading history. */
  @Transactional
  public void clear(Long userId) {
    postReadRepository.deleteByUserId(userId);
  }
}
