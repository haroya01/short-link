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

  /**
   * The caller's liked posts as full feed cards, newest-liked first. Stale likes (post
   * deleted/unpublished or author gone) are skipped — unpublished posts are filtered here and the
   * assembler drops deleted-author posts. Posts are batch-loaded to avoid an N+1 over the list.
   */
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
