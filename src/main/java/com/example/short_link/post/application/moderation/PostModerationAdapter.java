package com.example.short_link.post.application.moderation;

import com.example.short_link.common.post.PostModerationPort;
import com.example.short_link.post.application.write.UnpublishPostUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * post 슬라이스의 {@link PostModerationPort} 구현 — abuse 슬라이스의 신고 처리(resolve)가 post 에 직접 의존하지 않고 글 게시취소를
 * 집행하게 한다. 기존 관리자 takedown({@link UnpublishPostUseCase#adminExecute})을 그대로 포트로 묶는다(멱등·프로필 캐시 무효화
 * 포함). 호출자 트랜잭션 안에서 실행된다.
 */
@Component
@RequiredArgsConstructor
class PostModerationAdapter implements PostModerationPort {

  private final UnpublishPostUseCase unpublishPostUseCase;

  @Override
  public void unpublish(Long adminUserId, Long postId) {
    unpublishPostUseCase.adminExecute(adminUserId, postId);
  }
}
