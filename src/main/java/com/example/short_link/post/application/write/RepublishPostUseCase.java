package com.example.short_link.post.application.write;

import com.example.short_link.common.cache.ProfileCacheInvalidator;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RepublishPostUseCase {

  private final PostOwnership postOwnership;
  private final PostRepository postRepository;
  private final ProfileCacheInvalidator cacheEviction;

  @Transactional
  public PostEntity execute(RepublishPostCommand cmd) {
    PostEntity post = postOwnership.requireOwned(cmd.userId(), cmd.postId());
    post.republish();
    PostEntity saved = postRepository.save(post);
    // Republish (UNPUBLISHED→PUBLISHED) can flip hasBlog false→true; evict the profile.
    cacheEviction.evictByUserId(saved.getUserId());
    return saved;
  }
}
