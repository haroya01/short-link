package com.example.short_link.post.application.write;

import com.example.short_link.common.cache.ProfileCacheInvalidator;
import com.example.short_link.common.event.PostPublishedEvent;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PublishPostUseCase {

  private final PostOwnership postOwnership;
  private final PostRepository postRepository;
  private final PostRevisionCapture postRevisionCapture;
  private final ProfileCacheInvalidator cacheEviction;
  private final ApplicationEventPublisher events;

  @Transactional
  public PostEntity execute(PublishPostCommand cmd) {
    PostEntity post = postOwnership.requireOwned(cmd.userId(), cmd.postId());
    // publishedAt is stamped only on the very first publish and preserved across
    // unpublish/republish
    // — so a null here is exactly "never been public", which is when followers should be notified.
    boolean firstPublish = post.getPublishedAt() == null;
    post.publish();
    PostEntity saved = postRepository.save(post);
    postRevisionCapture.capture(saved);
    // First publish flips the author's public profile to hasBlog (publishedPostCount 0→1); evict so
    // the link-in-bio surface shows the blog entry-point immediately. Inline eviction matches the
    // existing AvatarService/OgOverrideService convention.
    cacheEviction.evictByUserId(saved.getUserId());
    if (firstPublish) {
      events.publishEvent(
          new PostPublishedEvent(
              saved.getUserId(), saved.getId(), saved.getSlug(), saved.getTitle(), Instant.now()));
    }
    return saved;
  }
}
