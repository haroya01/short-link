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

  /** 엔티티가 필요 없는 호출자를 위한 소유권 검사. 응답 데이터는 준비하지 않는다. */
  public void verifyOwned(Long userId, Long postId) {
    loadOwned(userId, postId);
  }

  private PostEntity loadOwned(Long userId, Long postId) {
    PostEntity post =
        postRepository
            .findById(postId)
            .orElseThrow(() -> new PostException(PostErrorCode.POST_NOT_FOUND, postId));
    if (!post.isOwnedBy(userId)) {
      throw new PostException(PostErrorCode.PERMISSION_DENIED).with("postId", postId);
    }
    return post;
  }
}
