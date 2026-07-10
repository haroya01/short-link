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

/** Highlight reads — public attributed list per post, and the viewer's own library. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostHighlightQueryService {

  private final PostHighlightRepository highlightRepository;
  private final PostHighlightReplyRepository replyRepository;
  private final PostRepository postRepository;
  private final UserRepository userRepository;
  private final FollowRepository followRepository;

  /** Public, attributed highlights on a published post (Medium-style social highlights). */
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

  /** The viewer's own highlights across all posts — the "my highlights" library. */
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
   * "남들 하이라이트" 피드 — 팔로우한 큐레이터가 최근 칠한 공개 구절을 최신순으로. 대상 글·큐레이터·작가·답글수를 일괄 해석(N+1 없이) 하고, 대상 글이 사라진
   * 하이라이트는 건너뛴다. 팔로우가 없으면 빈 피드(콜드스타트는 클라 안내).
   */
  public HighlightFeedView feed(Long userId, int page, int size) {
    List<Long> followingIds = followRepository.findFollowingIds(userId);
    if (followingIds.isEmpty()) {
      return new HighlightFeedView(List.of(), page, size, false);
    }

    List<PostHighlightEntity> highlights =
        highlightRepository.findByUserIdsOrderByCreatedAtDesc(followingIds, page, size);

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
                  if (post == null) return null; // 대상 글 소실 — 피드에서 뺀다.
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

    return new HighlightFeedView(items, page, size, highlights.size() == size);
  }
}
