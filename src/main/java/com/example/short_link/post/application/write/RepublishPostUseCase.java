package com.example.short_link.post.application.write;

import com.example.short_link.common.cache.ProfileCacheInvalidator;
import com.example.short_link.post.application.read.PostView;
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
  private final PostWriteViewAssembler writeViews;

  @Transactional
  public PostView execute(RepublishPostCommand cmd) {
    PostEntity post = postOwnership.requireOwnedForUpdate(cmd.userId(), cmd.postId());
    post.republish();
    PostEntity saved = postRepository.save(post);
    // 비공개 중 수정한 내용도 재발행 시점의 리비전으로 남긴다.
    postRevisionCapture.capture(saved);
    cacheEviction.evictByUserId(saved.getUserId());
    return writeViews.fromSaved(saved);
  }
}
