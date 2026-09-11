package com.example.short_link.post.application.write;

import com.example.short_link.post.application.read.PostView;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BackToDraftPostUseCase {

  private final PostOwnership postOwnership;
  private final PostRepository postRepository;
  private final PostWriteViewAssembler writeViews;

  @Transactional
  public PostView execute(BackToDraftPostCommand cmd) {
    PostEntity post = postOwnership.requireOwned(cmd.userId(), cmd.postId());
    post.backToDraft();
    return writeViews.fromSaved(postRepository.save(post));
  }
}
