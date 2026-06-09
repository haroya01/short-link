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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
  // Tag-side sentinel: a NUL char no real tag can equal (tags are trimmed, non-blank). Keeps
  // the union query valid when the user follows no tags (JPQL `in ()` is invalid).
  private static final List<String> NO_TAGS = List.of("\u0000");

  private final PostRepository postRepository;
  private final UserRepository userRepository;
  private final FollowRepository followRepository;
  private final SeriesSubscriptionRepository seriesSubscriptionRepository;
  private final TagPrefQueryService tagPrefQueryService;
  private final PostFeedItemAssembler feedItemAssembler;

  public PublicFeedView feed(String sort, String lang, int page, int size) {
    List<PostEntity> posts =
        "trending".equalsIgnoreCase(sort)
            ? postRepository.findPublishedTrending(lang, page, size)
            : postRepository.findPublishedRecent(lang, page, size);
    return assemble(posts, postRepository.countPublished(lang), page, size);
  }

  /** Posts carrying a tag (case-insensitive), newest first. */
  public PublicFeedView feedByTag(String tag, int page, int size) {
    List<PostEntity> posts = postRepository.findPublishedByTag(tag, page, size);
    return assemble(posts, postRepository.countPublishedByTag(tag), page, size);
  }

  /**
   * Free-text search across title / excerpt / tags / author handle. {@code sort} = recent|trending.
   */
  public PublicFeedView search(String query, String sort, String lang, int page, int size) {
    List<PostEntity> posts =
        "trending".equalsIgnoreCase(sort)
            ? postRepository.searchPublishedTrending(query, lang, page, size)
            : postRepository.searchPublished(query, lang, page, size);
    return assemble(posts, postRepository.countSearchPublished(query, lang), page, size);
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
   * The "following" feed — everything the user opted into, merged newest-first: posts from authors
   * they follow, new episodes of series they subscribe to, AND posts carrying a tag they follow (주제
   * 팔로우). Empty when the user follows/subscribes to nothing on all three axes.
   */
  public PublicFeedView feedFollowing(Long userId, int page, int size) {
    List<Long> followingIds = followRepository.findFollowingIds(userId);
    List<Long> subscribedSeriesIds = seriesSubscriptionRepository.findSubscribedSeriesIds(userId);
    // Match the tag query's lower() — followed tags are user-entered, compare case-insensitively.
    List<String> followedTags =
        tagPrefQueryService.get(userId).followed().stream()
            .map(t -> t.toLowerCase(Locale.ROOT))
            .toList();
    if (followingIds.isEmpty() && subscribedSeriesIds.isEmpty() && followedTags.isEmpty()) {
      return new PublicFeedView(List.of(), page, size, false);
    }
    List<Long> authorIds = followingIds.isEmpty() ? NONE : followingIds;
    List<Long> seriesIds = subscribedSeriesIds.isEmpty() ? NONE : subscribedSeriesIds;
    List<String> tags = followedTags.isEmpty() ? NO_TAGS : followedTags;
    List<PostEntity> posts =
        postRepository.findPublishedByAuthorsSeriesOrTags(authorIds, seriesIds, tags, page, size);
    long total = postRepository.countPublishedByAuthorsSeriesOrTags(authorIds, seriesIds, tags);

    // Annotate each card with why it matched (작가/시리즈/주제) so the UI can explain it. Keyed by post
    // id, not list index — the assembler drops deleted-author posts, so positions wouldn't line up.
    Set<Long> followingSet = Set.copyOf(followingIds);
    Set<Long> seriesSet = Set.copyOf(subscribedSeriesIds);
    Set<String> tagSet = Set.copyOf(followedTags);
    // Plain HashMap, not Collectors.toMap — a post with no matching signal maps to a null reason,
    // which toMap rejects. Absent key == null reason == unannotated card.
    Map<Long, FollowReason> reasonById = new HashMap<>();
    for (PostEntity p : posts) {
      FollowReason reason = followReason(p, followingSet, seriesSet, tagSet);
      if (reason != null) reasonById.put(p.getId(), reason);
    }

    boolean hasNext = (long) (page + 1) * size < total;
    List<PublicFeedItem> items =
        feedItemAssembler.assemble(posts).stream()
            .map(it -> it.withFollowReason(reasonById.get(it.id())))
            .toList();
    return new PublicFeedView(items, page, size, hasNext);
  }

  // AUTHOR > SERIES > TOPIC — the most direct relationship wins, so a followed author's post reads
  // as
  // "팔로잉" rather than incidentally "주제". For TOPIC, name the first followed tag the post carries.
  private FollowReason followReason(
      PostEntity post, Set<Long> followingSet, Set<Long> seriesSet, Set<String> tagSet) {
    if (followingSet.contains(post.getUserId())) return FollowReason.author();
    if (post.getSeriesId() != null && seriesSet.contains(post.getSeriesId())) {
      return FollowReason.series();
    }
    return post.getTags().stream()
        .filter(t -> tagSet.contains(t.toLowerCase(Locale.ROOT)))
        .findFirst()
        .map(FollowReason::topic)
        .orElse(null);
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
