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

/** Exposes only PUBLISHED posts from non-deleted authors. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicFeedQueryService {

  private final PostRepository postRepository;
  private final UserRepository userRepository;
  private final FollowRepository followRepository;
  private final SeriesSubscriptionRepository seriesSubscriptionRepository;
  private final TagPrefQueryService tagPrefQueryService;
  private final PostFeedItemAssembler feedItemAssembler;

  public PublicFeedView feed(PublicFeedQuery query) {
    return switch (query.selection()) {
      case PublicFeedQuery.Search search -> search(search, query.page(), query.size());
      case PublicFeedQuery.Tagged tagged -> tagged(tagged, query.page(), query.size());
      case PublicFeedQuery.Browse browse -> browse(browse, query.page(), query.size());
    };
  }

  private PublicFeedView browse(PublicFeedQuery.Browse browse, int page, int size) {
    String language = browse.language();
    List<PostEntity> posts =
        switch (browse.order()) {
          case RECENT -> postRepository.findPublishedRecent(language, page, size);
          case TRENDING -> postRepository.findPublishedTrending(language, page, size);
        };
    return assemble(posts, postRepository.countPublished(language), page, size);
  }

  private PublicFeedView tagged(PublicFeedQuery.Tagged tagged, int page, int size) {
    String tag = tagged.tag();
    List<PostEntity> posts = postRepository.findPublishedByTag(tag, page, size);
    return assemble(posts, postRepository.countPublishedByTag(tag), page, size);
  }

  private PublicFeedView search(PublicFeedQuery.Search search, int page, int size) {
    String text = search.text();
    String language = search.language();
    List<PostEntity> posts =
        switch (search.order()) {
          case RELEVANCE -> postRepository.searchPublishedByRelevance(text, language, page, size);
          case RECENT -> postRepository.searchPublished(text, language, page, size);
          case TRENDING -> postRepository.searchPublishedTrending(text, language, page, size);
        };
    return assemble(posts, postRepository.countSearchPublished(text, language), page, size);
  }

  public List<TagCount> popularTags(int limit) {
    return postRepository.findPopularTags(limit);
  }

  /**
   * Over-fetches to allow for deleted authors being removed, then preserves ranking when trimming.
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
   * Merges followed authors, subscribed series and followed tags, newest first. Returns empty when
   * none of those signals exist.
   */
  public PublicFeedView feedFollowing(Long userId, int page, int size) {
    List<Long> followingIds = followRepository.findFollowingIds(userId);
    List<Long> subscribedSeriesIds = seriesSubscriptionRepository.findSubscribedSeriesIds(userId);
    // Match the query's lower() comparison for user-entered tags.
    List<String> followedTags =
        tagPrefQueryService.get(userId).followed().stream()
            .map(t -> t.toLowerCase(Locale.ROOT))
            .toList();
    if (followingIds.isEmpty() && subscribedSeriesIds.isEmpty() && followedTags.isEmpty()) {
      return new PublicFeedView(List.of(), page, size, false);
    }
    List<PostEntity> posts =
        postRepository.findPublishedByAuthorsSeriesOrTags(
            followingIds, subscribedSeriesIds, followedTags, page, size);
    long total =
        postRepository.countPublishedByAuthorsSeriesOrTags(
            followingIds, subscribedSeriesIds, followedTags);

    // Key reasons by post ID: the assembler can remove deleted-author posts and shift positions.
    Set<Long> followingSet = Set.copyOf(followingIds);
    Set<Long> seriesSet = Set.copyOf(subscribedSeriesIds);
    Set<String> tagSet = Set.copyOf(followedTags);
    // Absent reasons leave cards unannotated; Collectors.toMap rejects null values.
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
