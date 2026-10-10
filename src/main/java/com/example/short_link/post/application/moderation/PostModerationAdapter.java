package com.example.short_link.post.application.moderation;

import com.example.short_link.common.post.PostModerationPort;
import com.example.short_link.post.application.write.TakeDownPostUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class PostModerationAdapter implements PostModerationPort {

  private final TakeDownPostUseCase takeDownPost;

  @Override
  public void takeDown(Long adminUserId, Long postId) {
    takeDownPost.takeDown(adminUserId, postId);
  }
}
