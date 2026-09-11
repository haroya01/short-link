package com.example.short_link.post.application.write;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** One scheduled publication commits or rolls back independently of the rest of the batch. */
@Service
@RequiredArgsConstructor
public class PublishScheduledPostUseCase {
  private final PostRepository postRepository;
  private final PostPublicationCompletion publicationCompletion;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean execute(Long postId, Instant now) {
    PostEntity post = postRepository.findByIdForUpdate(postId).orElse(null);
    // The work list may be stale: another worker published, the author cancelled, or rescheduled.
    if (post == null || !post.isScheduled() || post.getScheduledAt().isAfter(now)) return false;
    boolean firstPublish = post.getPublishedAt() == null;
    post.publish();
    postRepository.save(post);
    publicationCompletion.complete(post, firstPublish, () -> now);
    return true;
  }
}
