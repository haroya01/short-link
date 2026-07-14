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
  private final PostRevisionCapture postRevisionCapture;
  private final ProfileCacheInvalidator cacheEviction;

  @Transactional
  public PostEntity execute(RepublishPostCommand cmd) {
    PostEntity post = postOwnership.requireOwned(cmd.userId(), cmd.postId());
    post.republish();
    PostEntity saved = postRepository.save(post);
    // "공개되는 순간의 모습이 리비전" — 발행(PublishPost)·예약 발행과 같은 불변식. 없으면
    // 비공개 상태에서 고친 본문이 어느 스냅샷에도 안 남아, 이후 롤백이 그 시점으로 못 돌아간다.
    postRevisionCapture.capture(saved);
    // Republish (UNPUBLISHED→PUBLISHED) can flip hasBlog false→true; evict the profile.
    cacheEviction.evictByUserId(saved.getUserId());
    return saved;
  }
}
