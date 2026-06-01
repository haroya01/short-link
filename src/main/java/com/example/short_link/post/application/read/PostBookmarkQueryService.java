package com.example.short_link.post.application.read;

import com.example.short_link.post.domain.PostBookmarkEntity;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostBookmarkRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostBookmarkQueryService {

  private final PostBookmarkRepository postBookmarkRepository;
  private final PostRepository postRepository;
  private final UserRepository userRepository;

  public PostBookmarkStatus status(Long userId, Long postId) {
    boolean bookmarked =
        userId != null && postBookmarkRepository.existsByPostIdAndUserId(postId, userId);
    return new PostBookmarkStatus(bookmarked);
  }

  /**
   * The caller's reading list, newest-bookmarked first. Stale entries (post deleted/unpublished or
   * author gone) are skipped. Posts and authors are batch-loaded to avoid an N+1 over the list.
   */
  public List<BookmarkView> list(Long userId) {
    List<PostBookmarkEntity> bookmarks =
        postBookmarkRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
    if (bookmarks.isEmpty()) return List.of();

    List<Long> postIds = bookmarks.stream().map(PostBookmarkEntity::getPostId).toList();
    Map<Long, PostEntity> posts =
        postRepository.findAllByIdIn(postIds).stream()
            .filter(PostEntity::isPublished)
            .collect(Collectors.toMap(PostEntity::getId, Function.identity()));
    Map<Long, UserEntity> authors =
        userRepository
            .findAllByIdIn(posts.values().stream().map(PostEntity::getUserId).distinct().toList())
            .stream()
            .filter(u -> !u.isDeleted())
            .collect(Collectors.toMap(UserEntity::getId, Function.identity()));

    return bookmarks.stream()
        .map(b -> posts.get(b.getPostId()))
        .filter(p -> p != null && authors.containsKey(p.getUserId()))
        .map(
            p ->
                new BookmarkView(
                    p.getId(), authors.get(p.getUserId()).getUsername(), p.getTitle(), p.getSlug()))
        .toList();
  }
}
