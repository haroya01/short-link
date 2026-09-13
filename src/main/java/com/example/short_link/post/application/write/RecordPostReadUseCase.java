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
 * Rereading moves the history entry to the top. Missing or unpublished posts are silently ignored
 * because recording is a beacon.
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

  @Transactional
  public void remove(Long userId, Long postId) {
    postReadRepository.deleteByUserIdAndPostId(userId, postId);
  }

  @Transactional
  public void clear(Long userId) {
    postReadRepository.deleteByUserId(userId);
  }
}
