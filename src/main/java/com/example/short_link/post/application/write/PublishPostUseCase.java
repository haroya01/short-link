package com.example.short_link.post.application.write;

import com.example.short_link.common.user.UserModerationGuard;
import com.example.short_link.post.application.read.PostView;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PublishPostUseCase {

  private final UserModerationGuard moderation;
  private final PostOwnership postOwnership;
  private final PostNumbering numbering;
  private final PostRepository postRepository;
  private final PostPublicationCompletion publicationCompletion;
  private final PostWriteViewAssembler writeViews;

  @Transactional
  public PostView execute(PublishPostCommand cmd) {
    PostEntity post = postOwnership.requireOwnedForUpdate(cmd.userId(), cmd.postId());
    moderation.requireCanWrite(cmd.userId());
    // publishedAt은 재발행에도 유지되므로 null일 때만 최초 발행 알림을 보낸다.
    boolean firstPublish = post.getPublishedAt() == null;
    numbering.numberIfClientMade(post);
    post.publish();
    PostEntity saved = postRepository.save(post);
    publicationCompletion.complete(saved, firstPublish, Instant::now);
    return writeViews.fromSaved(saved);
  }
}
