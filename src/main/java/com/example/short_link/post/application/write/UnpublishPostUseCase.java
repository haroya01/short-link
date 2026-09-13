package com.example.short_link.post.application.write;

import com.example.short_link.common.cache.ProfileCacheInvalidator;
import com.example.short_link.post.application.read.PostView;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
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

  /** 관리자 권한은 HTTP 보안 계층에서 검사한다. 이미 내려간 글은 그대로 두고, 없는 글은 404다. adminUserId는 감사 로그용이다. */
  @Transactional
  public void adminExecute(Long adminUserId, Long postId) {
    log.info("admin post takedown: adminUserId={}, postId={}", adminUserId, postId);
    PostEntity post =
        postRepository
            .findByIdForUpdate(postId)
            .orElseThrow(() -> new PostException(PostErrorCode.POST_NOT_FOUND, postId));
    if (post.isUnpublished()) {
      return;
    }
    post.unpublish();
    PostEntity saved = postRepository.save(post);
    cacheEviction.evictByUserId(saved.getUserId());
  }
}
