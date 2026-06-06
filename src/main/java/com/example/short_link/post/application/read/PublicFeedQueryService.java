package com.example.short_link.post.application.read;

import com.example.short_link.post.domain.AuthorPostStats;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.TagCount;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.SeriesSubscriptionRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.FollowRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.ArrayList;
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

  // A sentinel id that matches no row — lets the union query stay valid when one side is empty
  // (JPQL `in ()` is invalid) without branching into separate queries.
  private static final List<Long> NONE = List.of(-1L);

  private final PostRepository postRepository;
  private final UserRepository userRepository;
  private final FollowRepository followRepository;
  private final SeriesSubscriptionRepository seriesSubscriptionRepository;
  private final PostFeedItemAssembler feedItemAssembler;

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

  /**
   * Free-text search across title / excerpt / tags / author handle. {@code sort} = recent|trending.
   */
  public PublicFeedView search(String query, String sort, int page, int size) {
    List<PostEntity> posts =
        "trending".equalsIgnoreCase(sort)
            ? postRepository.searchPublishedTrending(query, page, size)
            : postRepository.searchPublished(query, page, size);
    return assemble(posts, postRepository.countSearchPublished(query), page, size);
  }

  /** Most-used tags across published posts, most popular first — the 주제 index. */
  public List<TagCount> popularTags(int limit) {
    return postRepository.findPopularTags(limit);
  }

  /**
   * Authors for the discovery rail — most published posts first. Over-fetches so deleted authors
   * (filtered during hydration) don't shrink the list below {@code limit}, then trims to {@code
   * limit} while preserving the ranking order.
   */
  public List<SuggestedAuthorView> suggestedAuthors(int limit) {
    List<AuthorPostStats> ranked = postRepository.findTopAuthorStats(limit * 2);
    Map<Long, UserEntity> authors =
        userRepository
            .findAllByIdIn(ranked.stream().map(AuthorPostStats::authorId).toList())
            .stream()
            .filter(u -> !u.isDeleted())
            .collect(Collectors.toMap(UserEntity::getId, Function.identity()));
    return ranked.stream()
        .filter(s -> authors.containsKey(s.authorId()))
        .limit(limit)
        .map(
            s ->
                new SuggestedAuthorView(
                    PublicAuthorView.from(authors.get(s.authorId())), s.postCount()))
        .toList();
  }

  /**
   * The "following" feed — posts from authors the user follows AND new episodes of series the user
   * subscribes to, merged newest-first. Empty when the user follows/subscribes to nothing.
   */
  public PublicFeedView feedFollowing(Long userId, int page, int size) {
    List<Long> followingIds = followRepository.findFollowingIds(userId);
    List<Long> subscribedSeriesIds = seriesSubscriptionRepository.findSubscribedSeriesIds(userId);
    if (followingIds.isEmpty() && subscribedSeriesIds.isEmpty()) {
      return new PublicFeedView(List.of(), page, size, false);
    }
    List<Long> authorIds = followingIds.isEmpty() ? NONE : followingIds;
    List<Long> seriesIds = subscribedSeriesIds.isEmpty() ? NONE : subscribedSeriesIds;
    List<PostEntity> posts =
        postRepository.findPublishedByAuthorIdsOrSeriesIds(authorIds, seriesIds, page, size);
    long total = postRepository.countPublishedByAuthorIdsOrSeriesIds(authorIds, seriesIds);
    return assemble(posts, total, page, size);
  }

  /**
   * Popular posts grouped by tag — one section per topic, for the 인기 tab. For each of the top
   * {@code tagLimit} tags, the top {@code perTag} published posts (deleted-author posts filtered).
   */
  public List<TrendingTagSection> trendingByTag(int tagLimit, int perTag) {
    List<TrendingTagSection> sections = new ArrayList<>();
    for (TagCount tag : postRepository.findPopularTags(tagLimit)) {
      List<PublicFeedItem> posts =
          feedItemAssembler.assemble(postRepository.findPublishedByTag(tag.tag(), 0, perTag));
      if (!posts.isEmpty()) {
        sections.add(new TrendingTagSection(tag.tag(), tag.count(), posts));
      }
    }
    return sections;
  }

  private PublicFeedView assemble(List<PostEntity> posts, long total, int page, int size) {
    boolean hasNext = (long) (page + 1) * size < total;
    return new PublicFeedView(feedItemAssembler.assemble(posts), page, size, hasNext);
  }
}
