package com.example.short_link.post.application.moderation;

import com.example.short_link.common.post.PostModerationPort;
import com.example.short_link.post.application.write.UnpublishPostUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 멱등 게시취소와 프로필 캐시 무효화를 호출자 트랜잭션 안에서 실행한다. */
@Component
@RequiredArgsConstructor
class PostModerationAdapter implements PostModerationPort {

  private final UnpublishPostUseCase unpublishPostUseCase;

  @Override
  public void unpublish(Long adminUserId, Long postId) {
    unpublishPostUseCase.adminExecute(adminUserId, postId);
  }
}
