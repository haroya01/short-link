package com.example.short_link.post.application.write;

import com.example.short_link.common.user.UserModerationGuard;
import com.example.short_link.post.application.read.PostView;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SchedulePostUseCase {

  private final UserModerationGuard moderation;
  private final PostOwnership postOwnership;
  private final PostRepository postRepository;
  private final PostWriteViewAssembler writeViews;

  @Transactional
  public PostView execute(SchedulePostCommand cmd) {
    PostEntity post = postOwnership.requireOwnedForUpdate(cmd.userId(), cmd.postId());
    moderation.requireCanWrite(cmd.userId());
    post.schedule(cmd.scheduledAt());
    return writeViews.fromSaved(postRepository.save(post));
  }
}
