package com.example.short_link.post.application.write;

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

  private final PostOwnership postOwnership;
  private final PostRepository postRepository;
  private final PostPublicationCompletion publicationCompletion;
  private final PostWriteViewAssembler writeViews;

  @Transactional
  public PostView execute(PublishPostCommand cmd) {
    PostEntity post = postOwnership.requireOwned(cmd.userId(), cmd.postId());
    // publishedAt is stamped only on the very first publish and preserved across
    // unpublish/republish
    // — so a null here is exactly "never been public", which is when followers should be notified.
    boolean firstPublish = post.getPublishedAt() == null;
    post.publish();
    PostEntity saved = postRepository.save(post);
    publicationCompletion.complete(saved, firstPublish, Instant::now);
    return writeViews.fromSaved(saved);
  }
}
