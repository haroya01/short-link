package com.example.short_link.post.application.read;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostLikeEntity;
import com.example.short_link.post.domain.repository.PostLikeRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostLikeQueryService {

  private final PostRepository postRepository;
  private final PostLikeRepository postLikeRepository;
  private final PostFeedItemAssembler feedItemAssembler;

  public PostLikeStatus status(Long userId, Long postId) {
    PostEntity post =
        postRepository
            .findById(postId)
            .orElseThrow(() -> new PostException(PostErrorCode.POST_NOT_FOUND, postId));
    boolean liked = userId != null && postLikeRepository.existsByPostIdAndUserId(postId, userId);
    return new PostLikeStatus(post.getLikeCount(), liked);
  }

  /** Newest-liked first. Skips deleted/unpublished posts and deleted authors. */
  public List<PublicFeedItem> likedPosts(Long userId) {
    List<PostLikeEntity> likes = postLikeRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
    if (likes.isEmpty()) return List.of();

    List<Long> postIds = likes.stream().map(PostLikeEntity::getPostId).toList();
    Map<Long, PostEntity> published =
        postRepository.findAllByIdIn(postIds).stream()
            .filter(PostEntity::isPublished)
            .collect(Collectors.toMap(PostEntity::getId, Function.identity()));

    List<PostEntity> ordered =
        likes.stream().map(l -> published.get(l.getPostId())).filter(Objects::nonNull).toList();
    return feedItemAssembler.assemble(ordered);
  }
}
