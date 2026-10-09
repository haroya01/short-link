package com.example.short_link.post.application.read;

import com.example.short_link.post.domain.AuthorPostStats;
import com.example.short_link.post.domain.FollowingFeedRef;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.SeriesFeedNote;
import com.example.short_link.post.domain.SeriesItemType;
import com.example.short_link.post.domain.TagCount;
import com.example.short_link.post.domain.repository.FollowingFeedReader;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.SeriesItemReader;
import com.example.short_link.post.domain.repository.SeriesSubscriptionRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.FollowRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
public class PublicFeedQueryService {

  private final PostRepository postRepository;
  private final UserRepository userRepository;
  private final FollowRepository followRepository;
  private final SeriesSubscriptionRepository seriesSubscriptionRepository;
  private final TagPrefQueryService tagPrefQueryService;
  private final PostFeedItemAssembler feedItemAssembler;
  private final FollowingFeedReader followingFeedReader;
  private final SeriesItemReader seriesItemReader;

  public PublicFeedView feed(Long viewerId, PublicFeedQuery query) {
    return switch (query.selection()) {
      case PublicFeedQuery.Search search -> search(viewerId, search, query.page(), query.size());
      case PublicFeedQuery.Tagged tagged -> tagged(viewerId, tagged, query.page(), query.size());
      case PublicFeedQuery.Browse browse -> browse(viewerId, browse, query.page(), query.size());
    };
  }

  private PublicFeedView browse(Long viewerId, PublicFeedQuery.Browse browse, int page, int size) {
    String language = browse.language();
    List<PostEntity> posts =
        switch (browse.order()) {
          case RECENT -> postRepository.findPublishedRecent(viewerId, language, page, size);
          case TRENDING -> postRepository.findPublishedTrending(viewerId, language, page, size);
        };
    return assemble(posts, postRepository.countPublished(viewerId, language), page, size);
  }

  private PublicFeedView tagged(Long viewerId, PublicFeedQuery.Tagged tagged, int page, int size) {
    String tag = tagged.tag();
    List<PostEntity> posts =
        switch (tagged.order()) {
          case RECENT -> postRepository.findPublishedByTag(viewerId, tag, page, size);
          case TRENDING -> postRepository.findPublishedTrendingByTag(viewerId, tag, page, size);
        };
    return assemble(posts, postRepository.countPublishedByTag(viewerId, tag), page, size);
  }

  private PublicFeedView search(Long viewerId, PublicFeedQuery.Search search, int page, int size) {
    String text = search.text();
    String language = search.language();
    List<PostEntity> posts =
        switch (search.order()) {
          case RELEVANCE ->
              postRepository.searchPublishedByRelevance(viewerId, text, language, page, size);
          case RECENT -> postRepository.searchPublished(viewerId, text, language, page, size);
          case TRENDING ->
              postRepository.searchPublishedTrending(viewerId, text, language, page, size);
        };
    return assemble(
        posts, postRepository.countSearchPublished(viewerId, text, language), page, size);
  }

  public List<TagCount> popularTags(int limit) {
    return postRepository.findPopularTags(limit);
  }

  // Over-fetches to allow for deleted authors being removed, then preserves ranking when trimming.
  public List<SuggestedAuthorView> suggestedAuthors(Long viewerId, int limit) {
    List<AuthorPostStats> ranked = postRepository.findTopAuthorStats(viewerId, limit * 2);
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
    Following following =
        new Following(
            Set.copyOf(followingIds), Set.copyOf(subscribedSeriesIds), Set.copyOf(followedTags));
    if (subscribedSeriesIds.isEmpty()) {
      List<PostEntity> posts =
          postRepository.findPublishedByAuthorsSeriesOrTags(
              userId, followingIds, subscribedSeriesIds, followedTags, page, size);
      long total =
          postRepository.countPublishedByAuthorsSeriesOrTags(
              userId, followingIds, subscribedSeriesIds, followedTags);
      return new PublicFeedView(
          annotate(posts, following), page, size, (long) (page + 1) * size < total);
    }

    // A subscribed series can hold notes as well, so the page is cut from posts and notes together.
    List<FollowingFeedRef> refs =
        followingFeedReader.page(
            userId, followingIds, subscribedSeriesIds, followedTags, page * size, size);
    long total = followingFeedReader.count(userId, followingIds, subscribedSeriesIds, followedTags);
    List<Long> postIds =
        refs.stream()
            .filter(r -> r.type() == SeriesItemType.POST)
            .map(FollowingFeedRef::id)
            .toList();
    Map<Long, PostEntity> loaded =
        postIds.isEmpty()
            ? Map.of()
            : postRepository.findAllByIdIn(postIds).stream()
                .collect(Collectors.toMap(PostEntity::getId, Function.identity()));
    List<PostEntity> posts = postIds.stream().map(loaded::get).filter(Objects::nonNull).toList();
    return new PublicFeedView(
        annotate(posts, following),
        page,
        size,
        (long) (page + 1) * size < total,
        seriesNotes(refs.stream().filter(r -> r.type() == SeriesItemType.NOTE).toList()));
  }

  private record Following(Set<Long> authors, Set<Long> series, Set<String> tags) {}

  // Key reasons by post ID: the assembler can remove deleted-author posts and shift positions.
  // Absent reasons leave cards unannotated; Collectors.toMap rejects null values.
  private List<PublicFeedItem> annotate(List<PostEntity> posts, Following following) {
    Map<Long, FollowReason> reasonById = new HashMap<>();
    for (PostEntity p : posts) {
      FollowReason reason = followReason(p, following);
      if (reason != null) reasonById.put(p.getId(), reason);
    }
    return feedItemAssembler.assemble(posts).stream()
        .map(it -> it.withFollowReason(reasonById.get(it.id())))
        .toList();
  }

  private FollowReason followReason(PostEntity post, Following following) {
    if (following.authors().contains(post.getUserId())) return FollowReason.author();
    if (post.getSeriesId() != null && following.series().contains(post.getSeriesId())) {
      return FollowReason.series();
    }
    return post.getTags().stream()
        .filter(t -> following.tags().contains(t.toLowerCase(Locale.ROOT)))
        .findFirst()
        .map(FollowReason::topic)
        .orElse(null);
  }

  private List<FeedSeriesNote> seriesNotes(List<FollowingFeedRef> refs) {
    if (refs.isEmpty()) return List.of();
    Map<Long, SeriesFeedNote> notes =
        seriesItemReader.feedNotes(refs.stream().map(FollowingFeedRef::id).toList());
    return refs.stream()
        .map(ref -> notes.get(ref.id()))
        .filter(Objects::nonNull)
        .map(
            n ->
                new FeedSeriesNote(
                    n.id(),
                    new PublicAuthorView(
                        n.author().id(),
                        n.author().username(),
                        n.author().bio(),
                        n.author().avatarUrl(),
                        n.author().displayName()),
                    n.body(),
                    n.contentWarning(),
                    n.excerpt(),
                    n.createdAt(),
                    new FeedSeriesNote.SeriesRef(n.seriesId(), n.seriesSlug(), n.seriesTitle())))
        .toList();
  }

  public List<TrendingTagSection> trendingByTag(Long viewerId, int tagLimit, int perTag) {
    List<TrendingTagSection> sections = new ArrayList<>();
    for (TagCount tag : postRepository.findPopularTags(tagLimit)) {
      List<PublicFeedItem> posts =
          feedItemAssembler.assemble(
              postRepository.findPublishedTrendingByTag(viewerId, tag.tag(), 0, perTag));
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
