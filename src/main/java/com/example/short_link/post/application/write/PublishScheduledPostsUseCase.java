package com.example.short_link.post.application.write;

import com.example.short_link.post.domain.repository.PostRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 글별 트랜잭션으로 발행하며, 한 글이 실패해도 나머지 예약 글은 계속 처리한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublishScheduledPostsUseCase {

  private final PostRepository postRepository;
  private final PublishScheduledPostUseCase publishScheduledPost;

  public int execute(Instant now) {
    List<Long> due = postRepository.findScheduledDueIds(now);
    int published = 0;
    for (Long postId : due) {
      try {
        if (publishScheduledPost.execute(postId, now)) published++;
      } catch (RuntimeException e) {
        log.warn("scheduled publish skipped post {}: {}", postId, e.getMessage());
      }
    }
    return published;
  }
}
