package com.example.short_link.post.application.write;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostOwnership {

  private final PostRepository postRepository;

  public PostEntity requireOwned(Long userId, Long postId) {
    return loadOwned(userId, postId);
  }

  /** Lock the parent post before any post or child mutation, sharing the lifecycle write order. */
  public PostEntity requireOwnedForUpdate(Long userId, Long postId) {
    return verifyOwner(
        postRepository
            .findByIdForUpdate(postId)
            .orElseThrow(() -> new PostException(PostErrorCode.POST_NOT_FOUND, postId)),
        userId,
        postId);
  }

  public void verifyOwned(Long userId, Long postId) {
    loadOwned(userId, postId);
  }

  private PostEntity loadOwned(Long userId, Long postId) {
    PostEntity post =
        postRepository
            .findById(postId)
            .orElseThrow(() -> new PostException(PostErrorCode.POST_NOT_FOUND, postId));
    return verifyOwner(post, userId, postId);
  }

  private PostEntity verifyOwner(PostEntity post, Long userId, Long postId) {
    if (!post.isOwnedBy(userId)) {
      throw new PostException(PostErrorCode.PERMISSION_DENIED).with("postId", postId);
    }
    return post;
  }
}
