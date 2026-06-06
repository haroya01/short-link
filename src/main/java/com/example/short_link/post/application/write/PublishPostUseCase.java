package com.example.short_link.post.application.write;

import com.example.short_link.common.cache.ProfileCacheInvalidator;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PublishPostUseCase {

  private final PostOwnership postOwnership;
  private final PostRepository postRepository;
  private final PostRevisionCapture postRevisionCapture;
  private final ProfileCacheInvalidator cacheEviction;

  @Transactional
  public PostEntity execute(PublishPostCommand cmd) {
    PostEntity post = postOwnership.requireOwned(cmd.userId(), cmd.postId());
    post.publish();
    PostEntity saved = postRepository.save(post);
    postRevisionCapture.capture(saved);
    // First publish flips the author's public profile to hasBlog (publishedPostCount 0→1); evict so
    // the link-in-bio surface shows the blog entry-point immediately. Inline eviction matches the
    // existing AvatarService/OgOverrideService convention.
    cacheEviction.evictByUserId(saved.getUserId());
    return saved;
  }
}
