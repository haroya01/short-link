package com.example.short_link.post.application.read;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostLikeEntity;
import com.example.short_link.post.domain.PostReadEntity;
import com.example.short_link.post.domain.repository.PostLikeRepository;
import com.example.short_link.post.domain.repository.PostReadRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The "For You" feed — a personalized discovery feed ranked by tag affinity. Interest tags come
 * from what the reader explicitly follows plus the tags of posts they've actually read and liked;
 * candidates are recent published posts in those tags that they haven't read yet (and aren't their
 * own). A reader with no signal yet falls back to trending until we know them. Each card is
 * annotated with the interest tag it matched (왜 추천).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ForYouQueryService {

  /** Recent reads kept out of the feed (so it doesn't re-surface what you just read). */
  private static final int EXCLUDE_CAP = 200;

  /** Recent reads/likes mined for interest tags. */
  private static final int SIGNAL_CAP = 40;

  private static final int MAX_INTEREST_TAGS = 12;

  /** An explicit tag follow outweighs an incidental read. */
  private static final int FOLLOWED_WEIGHT = 3;

  /** No-match sentinel so the `not in` stays valid before the reader has read anything. */
  private static final List<Long> NO_EXCLUDE = List.of(-1L);

  private final PostRepository postRepository;
  private final PostReadRepository postReadRepository;
  private final PostLikeRepository postLikeRepository;
  private final TagPrefQueryService tagPrefQueryService;
  private final PostFeedItemAssembler feedItemAssembler;

  public PublicFeedView feedForYou(Long userId, int page, int size) {
    TagPrefsView prefs = tagPrefQueryService.get(userId);
    Set<String> hidden =
        prefs.hidden().stream().map(t -> t.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());

    List<Long> recentReadIds =
        postReadRepository.findByUserIdOrderByReadAtDesc(userId, 0, EXCLUDE_CAP).stream()
            .map(PostReadEntity::getPostId)
            .toList();
    List<Long> likedIds =
        postLikeRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
            .map(PostLikeEntity::getPostId)
            .limit(SIGNAL_CAP)
            .toList();

    List<String> interest = deriveInterestTags(prefs.followed(), recentReadIds, likedIds, hidden);
    if (interest.isEmpty()) {
      // Cold start — no signal yet. Trending is the honest default until we know the reader.
      List<PostEntity> trending = postRepository.findPublishedTrending(null, page, size);
      boolean hasNext = (long) (page + 1) * size < postRepository.countPublished(null);
      return new PublicFeedView(feedItemAssembler.assemble(trending), page, size, hasNext);
    }

    List<Long> exclude = recentReadIds.isEmpty() ? NO_EXCLUDE : recentReadIds;
    List<PostEntity> posts =
        postRepository.findForYouCandidates(userId, interest, exclude, page, size);
    long total = postRepository.countForYouCandidates(userId, interest, exclude);
    boolean hasNext = (long) (page + 1) * size < total;

    Set<String> interestSet = Set.copyOf(interest);
    Map<Long, FollowReason> reasonById = new HashMap<>();
    for (PostEntity p : posts) {
      p.getTags().stream()
          .filter(t -> interestSet.contains(t.toLowerCase(Locale.ROOT)))
          .findFirst()
          .ifPresent(t -> reasonById.put(p.getId(), FollowReason.topic(t)));
    }
    List<PublicFeedItem> items =
        feedItemAssembler.assemble(posts).stream()
            .map(it -> it.withFollowReason(reasonById.get(it.id())))
            .toList();
    return new PublicFeedView(items, page, size, hasNext);
  }

  /** followed tags (weighted) ∪ frequent tags from recent reads/likes, minus hidden — top N. */
  private List<String> deriveInterestTags(
      List<String> followed, List<Long> readIds, List<Long> likedIds, Set<String> hidden) {
    Map<String, Integer> freq = new HashMap<>();
    for (String t : followed) {
      freq.merge(t.toLowerCase(Locale.ROOT), FOLLOWED_WEIGHT, Integer::sum);
    }
    List<Long> signalIds =
        Stream.concat(readIds.stream().limit(SIGNAL_CAP), likedIds.stream()).distinct().toList();
    if (!signalIds.isEmpty()) {
      for (PostEntity p : postRepository.findAllByIdIn(signalIds)) {
        for (String t : p.getTags()) {
          freq.merge(t.toLowerCase(Locale.ROOT), 1, Integer::sum);
        }
      }
    }
    hidden.forEach(freq::remove);
    return freq.entrySet().stream()
        .sorted((a, b) -> b.getValue() - a.getValue())
        .limit(MAX_INTEREST_TAGS)
        .map(Map.Entry::getKey)
        .toList();
  }
}
