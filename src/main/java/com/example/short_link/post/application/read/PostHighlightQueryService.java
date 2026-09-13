package com.example.short_link.post.application.read;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostHighlightEntity;
import com.example.short_link.post.domain.repository.PostHighlightReplyRepository;
import com.example.short_link.post.domain.repository.PostHighlightRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.FollowRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostHighlightQueryService {

  private final PostHighlightRepository highlightRepository;
  private final PostHighlightReplyRepository replyRepository;
  private final PostRepository postRepository;
  private final UserRepository userRepository;
  private final FollowRepository followRepository;

  public List<HighlightView> listForPost(Long postId) {
    if (postRepository.findById(postId).filter(PostEntity::isPublished).isEmpty()) {
      return List.of();
    }
    List<PostHighlightEntity> highlights =
        highlightRepository.findAllByPostIdOrderByBlockOrderAscStartOffsetAsc(postId);
    List<Long> userIds =
        highlights.stream().map(PostHighlightEntity::getUserId).distinct().toList();
    Map<Long, UserEntity> users =
        userRepository.findAllByIdIn(userIds).stream()
            .collect(Collectors.toMap(UserEntity::getId, Function.identity()));
    Map<Long, Long> replyCounts =
        replyRepository.countByHighlightIds(
            highlights.stream().map(PostHighlightEntity::getId).toList());

    return highlights.stream()
        .map(
            h -> {
              UserEntity user = users.get(h.getUserId());
              return new HighlightView(
                  h.getId(),
                  user == null ? null : PublicAuthorView.from(user),
                  h.getBlockOrder(),
                  h.getEndBlockOrder(),
                  h.getStartOffset(),
                  h.getEndOffset(),
                  h.getQuote(),
                  h.getCreatedAt(),
                  h.getNote(),
                  replyCounts.getOrDefault(h.getId(), 0L));
            })
        .toList();
  }

  public List<MyHighlightView> listMine(Long userId) {
    List<PostHighlightEntity> highlights =
        highlightRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
    List<Long> postIds =
        highlights.stream().map(PostHighlightEntity::getPostId).distinct().toList();
    Map<Long, PostEntity> posts =
        postRepository.findAllByIdIn(postIds).stream()
            .collect(Collectors.toMap(PostEntity::getId, Function.identity()));
    List<Long> authorIds = posts.values().stream().map(PostEntity::getUserId).distinct().toList();
    Map<Long, UserEntity> authors =
        userRepository.findAllByIdIn(authorIds).stream()
            .collect(Collectors.toMap(UserEntity::getId, Function.identity()));

    return highlights.stream()
        .map(
            h -> {
              PostEntity post = posts.get(h.getPostId());
              UserEntity author = post == null ? null : authors.get(post.getUserId());
              return new MyHighlightView(
                  h.getId(),
                  h.getQuote(),
                  h.getBlockOrder(),
                  h.getEndBlockOrder(),
                  author == null ? null : author.getUsername(),
                  post == null ? null : post.getSlug(),
                  post == null ? null : post.getTitle(),
                  h.getCreatedAt(),
                  h.getNote());
            })
        .toList();
  }

  /**
   * 팔로우가 없거나 첫 페이지가 비면 전역 공개 하이라이트로 폴백한다. 이후 페이지의 빈 결과는 종료다. {@code forceGlobal}은 폴백 후 페이지네이션의 기준을
   * 전역으로 유지한다.
   */
  public HighlightFeedView feed(Long userId, int page, int size, boolean forceGlobal) {
    if (forceGlobal) {
      return globalFeed(page, size);
    }
    List<Long> followingIds = followRepository.findFollowingIds(userId);
    if (followingIds.isEmpty()) {
      return globalFeed(page, size);
    }

    List<PostHighlightEntity> highlights =
        highlightRepository.findByUserIdsOrderByCreatedAtDesc(followingIds, page, size);
    if (highlights.isEmpty() && page == 0) {
      return globalFeed(page, size);
    }
    return assemble(highlights, page, size, HighlightFeedView.SOURCE_FOLLOWING);
  }

  private HighlightFeedView globalFeed(int page, int size) {
    List<PostHighlightEntity> highlights =
        highlightRepository.findRecentOnPublishedPosts(page, size);
    return assemble(highlights, page, size, HighlightFeedView.SOURCE_GLOBAL);
  }

  private HighlightFeedView assemble(
      List<PostHighlightEntity> highlights, int page, int size, String source) {
    List<Long> postIds =
        highlights.stream().map(PostHighlightEntity::getPostId).distinct().toList();
    Map<Long, PostEntity> posts =
        postRepository.findAllByIdIn(postIds).stream()
            .collect(Collectors.toMap(PostEntity::getId, Function.identity()));

    Set<Long> userIds =
        highlights.stream()
            .map(PostHighlightEntity::getUserId)
            .collect(Collectors.toCollection(HashSet::new));
    posts.values().forEach(p -> userIds.add(p.getUserId()));
    Map<Long, UserEntity> users =
        userRepository.findAllByIdIn(userIds).stream()
            .collect(Collectors.toMap(UserEntity::getId, Function.identity()));

    Map<Long, Long> replyCounts =
        replyRepository.countByHighlightIds(
            highlights.stream().map(PostHighlightEntity::getId).toList());

    List<HighlightFeedItem> items =
        highlights.stream()
            .map(
                h -> {
                  PostEntity post = posts.get(h.getPostId());
                  if (post == null) return null;
                  UserEntity curator = users.get(h.getUserId());
                  UserEntity author = users.get(post.getUserId());
                  return new HighlightFeedItem(
                      h.getId(),
                      h.getPostId(),
                      curator == null ? null : PublicAuthorView.from(curator),
                      post.getSlug(),
                      post.getTitle(),
                      author == null ? null : author.getUsername(),
                      h.getBlockOrder(),
                      h.getEndBlockOrder(),
                      h.getStartOffset(),
                      h.getEndOffset(),
                      h.getQuote(),
                      h.getNote(),
                      h.getCreatedAt(),
                      replyCounts.getOrDefault(h.getId(), 0L));
                })
            .filter(Objects::nonNull)
            .toList();

    return new HighlightFeedView(items, page, size, highlights.size() == size, source);
  }
}
