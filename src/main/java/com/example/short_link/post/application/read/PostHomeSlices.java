package com.example.short_link.post.application.read;

import com.example.short_link.common.seen.Unseen;
import com.example.short_link.post.application.access.PostAccessPolicy;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
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
public class PostHomeSlices {

  private final PostRepository postRepository;
  private final PostFeedItemAssembler feedItemAssembler;
  private final FollowedTopics followedTopics;

  public PostSlice recentAt(Long viewerId, int offset, int limit, Unseen unseen) {
    List<PostEntity> rows = postRepository.findHomeRecentAt(viewerId, offset, limit, unseen);
    return slice(rows, feedItemAssembler.assemble(rows), offset, limit, viewerId);
  }

  public List<PooledPost> pool(Long viewerId, Instant since, Instant until, int limit) {
    return postRepository.findHomePool(viewerId, since, until, limit).stream()
        .map(
            row ->
                new PooledPost(
                    row.id(),
                    row.userId(),
                    row.language(),
                    row.publishedAt(),
                    row.tags(),
                    row.likes(),
                    row.quotes(),
                    row.highlights()))
        .toList();
  }

  // In the order asked, leaving out a post no longer listed.
  public List<PostCard> cards(Long viewerId, List<Long> postIds) {
    if (postIds.isEmpty()) {
      return List.of();
    }
    Map<Long, PostEntity> byId = new HashMap<>();
    postRepository.findAllByIdIn(postIds).forEach(post -> byId.put(post.getId(), post));
    List<PostEntity> rows =
        postIds.stream()
            .map(byId::get)
            .filter(post -> post != null && PostAccessPolicy.isListed(post))
            .toList();
    return slice(rows, feedItemAssembler.assemble(rows), 0, rows.size(), viewerId).items().stream()
        .map(PostSlice.At::post)
        .toList();
  }

  public BlogSlice blogBelow(Long viewerId, Instant publishedAt, Long id, int limit) {
    List<PostEntity> rows = postRepository.findBlogBelow(viewerId, publishedAt, id, limit + 1);
    boolean more = rows.size() > limit;
    return blog(more ? rows.subList(0, limit) : rows, more, viewerId);
  }

  public BlogSlice blogAbove(Long viewerId, Instant publishedAt, long id, int limit) {
    List<PostEntity> rows =
        new ArrayList<>(postRepository.findBlogAbove(viewerId, publishedAt, id, limit + 1));
    boolean more = rows.size() > limit;
    List<PostEntity> nearest = more ? rows.subList(0, limit) : rows;
    return blog(nearest.reversed(), more, viewerId);
  }

  private BlogSlice blog(List<PostEntity> rows, boolean more, Long viewerId) {
    if (rows.isEmpty()) {
      return BlogSlice.EMPTY;
    }
    List<PostCard> cards =
        slice(rows, feedItemAssembler.assemble(rows), 0, rows.size(), viewerId).items().stream()
            .map(PostSlice.At::post)
            .toList();
    PostEntity first = rows.getFirst();
    PostEntity last = rows.getLast();
    return new BlogSlice(
        cards, first.getPublishedAt(), first.getId(), last.getPublishedAt(), last.getId(), more);
  }

  public PostSlice roomAt(Long roomId, Long viewerId, int offset, int limit) {
    List<PostEntity> rows = postRepository.findInRoomAt(roomId, viewerId, offset, limit);
    return slice(rows, feedItemAssembler.assemble(rows), offset, limit, viewerId);
  }

  public PostSlice roomsAt(Long viewerId, int offset, int limit) {
    List<PostEntity> rows = postRepository.findInRoomsOfAt(viewerId, offset, limit);
    return slice(rows, feedItemAssembler.assemble(rows), offset, limit, viewerId);
  }

  public PostSlice trendingAt(Long viewerId, int offset, int limit) {
    List<PostEntity> rows = postRepository.findPublishedTrendingAt(viewerId, offset, limit);
    return slice(rows, feedItemAssembler.assemble(rows), offset, limit, viewerId);
  }

  public PostSlice followingAt(Long userId, int offset, int limit) {
    FollowedTopics.Following following = followedTopics.of(userId);
    List<PostEntity> rows =
        postRepository.findHomeFollowingAt(
            userId, following.authorIds(), following.seriesIds(), following.tags(), offset, limit);
    return slice(rows, followedTopics.annotate(rows, following), offset, limit, userId);
  }

  private PostSlice slice(
      List<PostEntity> rows, List<PublicFeedItem> shown, int offset, int limit, Long viewerId) {
    Map<Long, PublicFeedItem> byId =
        shown.stream()
            .collect(Collectors.toMap(PublicFeedItem::id, Function.identity(), (a, b) -> a));
    Map<Long, PostRepository.CardMarks> marks =
        byId.isEmpty() ? Map.of() : postRepository.cardMarks(byId.keySet(), viewerId);
    List<PostSlice.At> items = new ArrayList<>(rows.size());
    for (int i = 0; i < rows.size(); i++) {
      PostEntity row = rows.get(i);
      PublicFeedItem item = byId.get(row.getId());
      if (item != null) {
        PostRepository.CardMarks mark =
            marks.getOrDefault(row.getId(), new PostRepository.CardMarks(0, false));
        items.add(
            new PostSlice.At(
                offset + i,
                new PostCard(
                    item,
                    PostCard.readMinutes(row.getBodyTextLength()),
                    mark.comments(),
                    viewerId == null ? null : mark.liked())));
      }
    }
    return new PostSlice(items, rows.size() < limit);
  }
}
