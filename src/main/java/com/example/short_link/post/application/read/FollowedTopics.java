package com.example.short_link.post.application.read;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.SeriesSubscriptionRepository;
import com.example.short_link.user.application.read.FollowingReader;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class FollowedTopics {

  private final FollowingReader followingReader;
  private final SeriesSubscriptionRepository seriesSubscriptionRepository;
  private final TagPrefQueryService tagPrefQueryService;
  private final PostFeedItemAssembler feedItemAssembler;

  record Following(List<Long> authorIds, List<Long> seriesIds, List<String> tags) {

    boolean none() {
      return authorIds.isEmpty() && seriesIds.isEmpty() && tags.isEmpty();
    }

    Set<Long> authors() {
      return Set.copyOf(authorIds);
    }

    Set<Long> series() {
      return Set.copyOf(seriesIds);
    }
  }

  Following of(Long userId) {
    List<Long> followingIds = followingReader.followingIds(userId);
    List<Long> subscribedSeriesIds = seriesSubscriptionRepository.findSubscribedSeriesIds(userId);
    // Match the query's lower() comparison for user-entered tags.
    List<String> followedTags =
        tagPrefQueryService.get(userId).followed().stream()
            .map(t -> t.toLowerCase(Locale.ROOT))
            .toList();
    return new Following(followingIds, subscribedSeriesIds, followedTags);
  }

  // Keyed by post id because the assembler may drop posts and shift positions. Collectors.toMap
  // rejects null values.
  List<PublicFeedItem> annotate(List<PostEntity> posts, Following following) {
    Set<Long> authors = following.authors();
    Set<Long> series = following.series();
    Set<String> tags = Set.copyOf(following.tags());
    Map<Long, FollowReason> reasonById = new HashMap<>();
    for (PostEntity p : posts) {
      FollowReason reason = followReason(p, authors, series, tags);
      if (reason != null) reasonById.put(p.getId(), reason);
    }
    return feedItemAssembler.assemble(posts).stream()
        .map(it -> it.withFollowReason(reasonById.get(it.id())))
        .toList();
  }

  private static FollowReason followReason(
      PostEntity post, Set<Long> authors, Set<Long> series, Set<String> tags) {
    if (post.isInRoom()) return null;
    if (authors.contains(post.getUserId())) return FollowReason.author();
    if (post.getSeriesId() != null && series.contains(post.getSeriesId())) {
      return FollowReason.series();
    }
    return post.getTags().stream()
        .filter(t -> tags.contains(t.toLowerCase(Locale.ROOT)))
        .findFirst()
        .map(FollowReason::topic)
        .orElse(null);
  }
}
