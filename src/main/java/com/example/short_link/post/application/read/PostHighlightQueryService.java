package com.example.short_link.post.application.read;

import com.example.short_link.post.application.access.PostAccessPolicy;
import com.example.short_link.post.application.access.PostReader;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostHighlightEntity;
import com.example.short_link.post.domain.repository.PostHighlightReplyRepository;
import com.example.short_link.post.domain.repository.PostHighlightRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.user.application.read.FollowingReader;
import com.example.short_link.user.application.read.UserSummary;
import com.example.short_link.user.application.read.UserSummaryReader;
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
  private final UserSummaryReader userSummaries;
  private final FollowingReader following;
  private final PostAccessPolicy access;

  public PostScoped<List<HighlightView>> listForPost(Long postId, PostReader reader) {
    PostEntity post = postRepository.findById(postId).orElse(null);
    if (post == null || !access.readable(post, reader)) {
      return new PostScoped<>(List.of(), false);
    }
    return new PostScoped<>(highlights(postId, reader.viewerId()), !PostAccessPolicy.isOpen(post));
  }

  private List<HighlightView> highlights(Long postId, Long viewerId) {
    List<PostHighlightEntity> highlights = highlightRepository.findHeardByPostId(postId, viewerId);
    List<Long> userIds =
        highlights.stream().map(PostHighlightEntity::getUserId).distinct().toList();
    Map<Long, UserSummary> users =
        userSummaries.findAll(userIds).stream()
            .collect(Collectors.toMap(UserSummary::id, Function.identity()));
    Map<Long, Long> replyCounts =
        replyRepository.countByHighlightIds(
            highlights.stream().map(PostHighlightEntity::getId).toList());

    return highlights.stream()
        .map(
            h -> {
              UserSummary user = users.get(h.getUserId());
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
    PostAccessPolicy.Lists lists = access.forLists(PostReader.of(userId));
    Map<Long, PostEntity> posts =
        postRepository.findAllByIdIn(postIds).stream()
            .collect(Collectors.toMap(PostEntity::getId, Function.identity()));
    Set<Long> left =
        posts.values().stream()
            .filter(post -> !lists.keeps(post))
            .map(PostEntity::getId)
            .collect(Collectors.toSet());
    posts.keySet().removeAll(left);
    List<Long> authorIds = posts.values().stream().map(PostEntity::getUserId).distinct().toList();
    Map<Long, UserSummary> authors =
        userSummaries.findAll(authorIds).stream()
            .collect(Collectors.toMap(UserSummary::id, Function.identity()));

    return highlights.stream()
        .filter(h -> !left.contains(h.getPostId()))
        .map(
            h -> {
              PostEntity post = posts.get(h.getPostId());
              UserSummary author = post == null ? null : authors.get(post.getUserId());
              return new MyHighlightView(
                  h.getId(),
                  h.getQuote(),
                  h.getBlockOrder(),
                  h.getEndBlockOrder(),
                  author == null ? null : author.username(),
                  post == null ? null : post.getSlug(),
                  post == null ? null : post.getTitle(),
                  h.getCreatedAt(),
                  h.getNote());
            })
        .toList();
  }

  // 팔로우가 없거나 첫 페이지가 비면 전역 공개 하이라이트로 폴백한다. 이후 페이지의 빈 결과는 종료다. forceGlobal은 폴백 후 페이지네이션의 기준을 전역으로
  // 유지한다.
  public HighlightFeedView feed(Long userId, int page, int size, boolean forceGlobal) {
    if (forceGlobal) {
      return globalFeed(userId, page, size);
    }
    List<Long> followingIds = following.followingIds(userId);
    if (followingIds.isEmpty()) {
      return globalFeed(userId, page, size);
    }

    List<PostHighlightEntity> highlights =
        highlightRepository.findByUserIdsOrderByCreatedAtDesc(followingIds, userId, page, size);
    if (highlights.isEmpty() && page == 0) {
      return globalFeed(userId, page, size);
    }
    return assemble(highlights, page, size, HighlightFeedView.SOURCE_FOLLOWING);
  }

  private HighlightFeedView globalFeed(Long viewerId, int page, int size) {
    List<PostHighlightEntity> highlights =
        highlightRepository.findRecentOnPublishedPosts(viewerId, page, size);
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
    Map<Long, UserSummary> users =
        userSummaries.findAll(userIds).stream()
            .collect(Collectors.toMap(UserSummary::id, Function.identity()));

    Map<Long, Long> replyCounts =
        replyRepository.countByHighlightIds(
            highlights.stream().map(PostHighlightEntity::getId).toList());

    List<HighlightFeedItem> items =
        highlights.stream()
            .map(
                h -> {
                  PostEntity post = posts.get(h.getPostId());
                  if (post == null) return null;
                  UserSummary curator = users.get(h.getUserId());
                  UserSummary author = users.get(post.getUserId());
                  return new HighlightFeedItem(
                      h.getId(),
                      h.getPostId(),
                      curator == null ? null : PublicAuthorView.from(curator),
                      post.getSlug(),
                      post.getTitle(),
                      author == null ? null : author.username(),
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
