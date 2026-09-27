package com.example.short_link.post.application.moderation;

import com.example.short_link.common.post.PostModerationPort;
import com.example.short_link.post.application.write.UnpublishPostUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class PostModerationAdapter implements PostModerationPort {

  private final UnpublishPostUseCase unpublishPostUseCase;

  @Override
  public void unpublish(Long adminUserId, Long postId) {
    unpublishPostUseCase.adminExecute(adminUserId, postId);
  }
}
