package com.example.short_link.post.application.read;

import com.example.short_link.post.application.access.PostAccessPolicy;
import com.example.short_link.post.application.access.PostReader;
import com.example.short_link.post.domain.PostBookmarkEntity;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostBookmarkRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.user.application.read.UserSummary;
import com.example.short_link.user.application.read.UserSummaryReader;
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
  private final UserSummaryReader userSummaries;
  private final PostAccessPolicy access;

  public PostBookmarkStatus status(Long userId, Long postId) {
    boolean bookmarked =
        userId != null && postBookmarkRepository.existsByPostIdAndUserId(postId, userId);
    return new PostBookmarkStatus(bookmarked);
  }

  public List<BookmarkView> list(Long userId) {
    List<PostBookmarkEntity> bookmarks =
        postBookmarkRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
    if (bookmarks.isEmpty()) return List.of();

    PostAccessPolicy.Lists lists = access.forLists(PostReader.of(userId));
    List<Long> postIds = bookmarks.stream().map(PostBookmarkEntity::getPostId).toList();
    Map<Long, PostEntity> posts =
        postRepository.findAllByIdIn(postIds).stream()
            .filter(post -> !lists.card(post).hidden())
            .collect(Collectors.toMap(PostEntity::getId, Function.identity()));
    Map<Long, UserSummary> authors =
        userSummaries
            .findAllActive(posts.values().stream().map(PostEntity::getUserId).distinct().toList())
            .stream()
            .collect(Collectors.toMap(UserSummary::id, Function.identity()));

    return bookmarks.stream()
        .map(b -> posts.get(b.getPostId()))
        .filter(p -> p != null && authors.containsKey(p.getUserId()))
        .map(
            p ->
                new BookmarkView(
                    p.getId(), authors.get(p.getUserId()).username(), p.getTitle(), p.getSlug()))
        .toList();
  }
}
