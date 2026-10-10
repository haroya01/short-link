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
public class UnpublishPostUseCase {

  private final PostOwnership postOwnership;
  private final PostRepository postRepository;
  private final ProfileCacheInvalidator cacheEviction;
  private final PostWriteViewAssembler writeViews;

  @Transactional
  public PostView execute(UnpublishPostCommand cmd) {
    PostEntity post = postOwnership.requireOwnedForUpdate(cmd.userId(), cmd.postId());
    post.unpublish();
    PostEntity saved = postRepository.save(post);
    // 마지막 공개 글을 내리면 프로필의 블로그 진입점도 사라져야 한다.
    cacheEviction.evictByUserId(saved.getUserId());
    return writeViews.fromSaved(saved);
  }
}
