package com.example.short_link.post.application.write;

import com.example.short_link.post.application.access.PostAccessPolicy;
import com.example.short_link.post.application.access.PostViewer;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostReadEntity;
import com.example.short_link.post.domain.repository.PostReadRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Missing or unpublished posts are silently ignored because recording is a beacon.
@Service
@RequiredArgsConstructor
public class RecordPostReadUseCase {

  private final PostRepository postRepository;
  private final PostReadRepository postReadRepository;
  private final PostAccessPolicy access;

  @Transactional
  public void record(PostViewer reader, Long postId) {
    Long userId = reader.viewerId();
    PostEntity post = postRepository.findById(postId).orElse(null);
    if (post == null || !access.decide(post, reader).full()) {
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
