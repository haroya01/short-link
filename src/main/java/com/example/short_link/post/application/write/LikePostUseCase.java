package com.example.short_link.post.application.write;

import com.example.short_link.post.application.read.PostLikeStatus;
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
    requirePost(postId);
    // Insert + counter bump are both atomic at the DB level, so concurrent likes from different
    // users can't lose an increment, and a double-like from the same user bumps the count once.
    if (postLikeRepository.insertIgnore(postId, userId) > 0) {
      postRepository.incrementLikeCount(postId);
    }
    return new PostLikeStatus(postLikeRepository.countByPostId(postId), true);
  }

  @Transactional
  public PostLikeStatus unlike(Long userId, Long postId) {
    requirePost(postId);
    if (postLikeRepository.deleteByPostIdAndUserId(postId, userId) > 0) {
      postRepository.decrementLikeCount(postId);
    }
    return new PostLikeStatus(postLikeRepository.countByPostId(postId), false);
  }

  private void requirePost(Long postId) {
    if (postRepository.findById(postId).isEmpty()) {
      throw new PostException(PostErrorCode.POST_NOT_FOUND, postId);
    }
  }
}
