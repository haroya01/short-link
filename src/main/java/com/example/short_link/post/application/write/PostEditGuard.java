package com.example.short_link.post.application.write;

import com.example.short_link.post.domain.PostEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 엔티티를 바꾸지 않는다. 뒤따르는 조회가 자동 플러시로 UPDATE를 먼저 내보내지 않게 하려는 것이다.
@Component
@RequiredArgsConstructor
public class PostEditGuard {

  private final PostRevisionCapture revisions;

  public void check(PostEntity post, Long baseVersion, boolean overwrite) {
    if (overwrite) {
      revisions.capture(post);
    } else if (baseVersion != null) {
      post.requireContentVersion(baseVersion);
    }
  }
}
