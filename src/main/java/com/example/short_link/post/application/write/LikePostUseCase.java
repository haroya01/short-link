package com.example.short_link.post.application.write;

import com.example.short_link.post.application.read.PostLikeStatus;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostLikeEntity;
import com.example.short_link.post.domain.repository.PostLikeRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LikePostUseCase {

  private final PostRepository postRepository;
  private final PostLikeRepository postLikeRepository;

  @Transactional
  public PostLikeStatus like(Long userId, Long postId) {
    PostEntity post = requirePost(postId);
    if (!postLikeRepository.existsByPostIdAndUserId(postId, userId)) {
      postLikeRepository.save(new PostLikeEntity(postId, userId));
      post.incrementLikeCount();
      postRepository.save(post);
    }
    return new PostLikeStatus(post.getLikeCount(), true);
  }

  @Transactional
  public PostLikeStatus unlike(Long userId, Long postId) {
    PostEntity post = requirePost(postId);
    postLikeRepository
        .findByPostIdAndUserId(postId, userId)
        .ifPresent(
            like -> {
              postLikeRepository.delete(like);
              post.decrementLikeCount();
              postRepository.save(post);
            });
    return new PostLikeStatus(post.getLikeCount(), false);
  }

  private PostEntity requirePost(Long postId) {
    return postRepository
        .findById(postId)
        .orElseThrow(() -> new PostException(PostErrorCode.POST_NOT_FOUND, postId));
  }
}
