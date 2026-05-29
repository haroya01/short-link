package com.example.short_link.post.application.read;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.FollowRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Global public feed across all authors — the velog-style home where anyone browses published
 * posts. Only PUBLISHED posts from non-deleted authors are exposed. Authors are batch-hydrated to
 * avoid per-post N+1.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicFeedQueryService {

  private final PostRepository postRepository;
  private final UserRepository userRepository;
  private final FollowRepository followRepository;

  public PublicFeedView feed(String sort, int page, int size) {
    List<PostEntity> posts =
        "trending".equalsIgnoreCase(sort)
            ? postRepository.findPublishedTrending(page, size)
            : postRepository.findPublishedRecent(page, size);
    return assemble(posts, postRepository.countPublished(), page, size);
  }

  /** Posts carrying a tag (case-insensitive), newest first. */
  public PublicFeedView feedByTag(String tag, int page, int size) {
    List<PostEntity> posts = postRepository.findPublishedByTag(tag, page, size);
    return assemble(posts, postRepository.countPublishedByTag(tag), page, size);
  }

  /** Posts from authors the user follows, newest first. Empty when the user follows no one. */
  public PublicFeedView feedFollowing(Long userId, int page, int size) {
    List<Long> followingIds = followRepository.findFollowingIds(userId);
    if (followingIds.isEmpty()) {
      return new PublicFeedView(List.of(), page, size, false);
    }
    List<PostEntity> posts = postRepository.findPublishedByAuthorIds(followingIds, page, size);
    return assemble(posts, postRepository.countPublishedByAuthorIds(followingIds), page, size);
  }

  private PublicFeedView assemble(List<PostEntity> posts, long total, int page, int size) {
    List<Long> authorIds = posts.stream().map(PostEntity::getUserId).distinct().toList();
    Map<Long, UserEntity> authors =
        userRepository.findAllByIdIn(authorIds).stream()
            .filter(u -> !u.isDeleted())
            .collect(Collectors.toMap(UserEntity::getId, Function.identity()));

    List<PublicFeedItem> items =
        posts.stream()
            .filter(p -> authors.containsKey(p.getUserId()))
            .map(p -> toItem(p, authors.get(p.getUserId())))
            .toList();

    boolean hasNext = (long) (page + 1) * size < total;
    return new PublicFeedView(items, page, size, hasNext);
  }

  private PublicFeedItem toItem(PostEntity post, UserEntity author) {
    return new PublicFeedItem(
        PublicAuthorView.from(author),
        post.getSlug(),
        post.getTitle(),
        post.getExcerpt(),
        post.getOgImageUrl(),
        post.getLanguageTag(),
        List.copyOf(post.getTags()),
        post.getPublishedAt(),
        post.getViewCount(),
        post.getLikeCount());
  }
}
