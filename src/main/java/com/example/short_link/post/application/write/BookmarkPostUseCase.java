package com.example.short_link.post.application.write;

import com.example.short_link.post.application.access.PostAccessPolicy;
import com.example.short_link.post.application.access.PostViewer;
import com.example.short_link.post.application.read.PostBookmarkStatus;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostBookmarkRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookmarkPostUseCase {

  private final PostRepository postRepository;
  private final PostBookmarkRepository postBookmarkRepository;
  private final PostAccessPolicy access;

  @Transactional
  public PostBookmarkStatus bookmark(PostViewer reader, Long postId) {
    access.requireReadable(requirePost(postId), reader);
    postBookmarkRepository.insertIgnore(postId, reader.viewerId());
    return new PostBookmarkStatus(true);
  }

  @Transactional
  public PostBookmarkStatus unbookmark(Long userId, Long postId) {
    postBookmarkRepository
        .findByPostIdAndUserId(postId, userId)
        .ifPresent(postBookmarkRepository::delete);
    return new PostBookmarkStatus(false);
  }

  private PostEntity requirePost(Long postId) {
    return postRepository
        .findById(postId)
        .orElseThrow(() -> new PostException(PostErrorCode.POST_NOT_FOUND, postId));
  }
}
