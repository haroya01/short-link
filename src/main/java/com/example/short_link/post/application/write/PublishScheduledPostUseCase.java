package com.example.short_link.post.application.write;

import com.example.short_link.common.user.UserModerationGuard;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PublishScheduledPostUseCase {
  private final PostRepository postRepository;
  private final PostPublicationCompletion publicationCompletion;
  private final UserModerationGuard moderation;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean execute(Long postId, Instant now) {
    PostEntity post = postRepository.findByIdForUpdate(postId).orElse(null);
    // The work list may be stale: another worker published, the author cancelled, or rescheduled.
    if (post == null || !post.isScheduled() || post.getScheduledAt().isAfter(now)) return false;
    if (!moderation.canWrite(post.getUserId())) {
      log.info(
          "scheduled publish withheld, author cannot write: postId={}, userId={}",
          postId,
          post.getUserId());
      post.backToDraft();
      postRepository.save(post);
      return false;
    }
    boolean firstPublish = post.getPublishedAt() == null;
    post.publish();
    postRepository.save(post);
    publicationCompletion.complete(post, firstPublish, () -> now);
    return true;
  }
}
