package com.example.short_link.post.application.read;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostLikeEntity;
import com.example.short_link.post.domain.PostReadEntity;
import com.example.short_link.post.domain.feed.FeedCandidate;
import com.example.short_link.post.domain.feed.FeedRanking;
import com.example.short_link.post.domain.feed.ForYouRanking;
import com.example.short_link.post.domain.feed.InterestProfile;
import com.example.short_link.post.domain.repository.PostLikeRepository;
import com.example.short_link.post.domain.repository.PostReadRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.PostViewEventRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ForYouQueryService {

  private final PostRepository postRepository;
  private final PostReadRepository postReadRepository;
  private final PostLikeRepository postLikeRepository;
  private final PostViewEventRepository postViewEventRepository;
  private final TagPrefQueryService tagPrefQueryService;
  private final UserRepository userRepository;
  private final PostFeedItemAssembler feedItemAssembler;
  private final Clock clock;

  public PublicFeedView feedForYou(Long userId, int page, int size) {
    TagPrefsView prefs = tagPrefQueryService.get(userId);
    List<Long> readIds =
        postReadRepository
            .findByUserIdOrderByReadAtDesc(userId, 0, FeedRanking.EXCLUDED_READS)
            .stream()
            .map(PostReadEntity::getPostId)
            .toList();
    List<Long> likedIds =
        postLikeRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
            .map(PostLikeEntity::getPostId)
            .toList();
    String locale = userRepository.findById(userId).map(UserEntity::getLocale).orElse(null);
    ForYouRanking.Reader reader =
        ForYouRanking.Reader.of(
            userId,
            locale,
            prefs.followed(),
            prefs.hidden(),
            readIds,
            likedIds,
            candidatesById(InterestProfile.signalPostIds(readIds, likedIds)));

    Instant now = clock.instant();
    List<FeedCandidate> pool = postRepository.findFeedCandidates(FeedRanking.CANDIDATE_POOL_SIZE);
    Map<Long, Long> views =
        postViewEventRepository.countHumanViewsSince(
            pool.stream().map(FeedCandidate::postId).toList(),
            now.minus(FeedRanking.TRENDING_WINDOW));
    List<FeedCandidate> ranked = ForYouRanking.rank(pool, reader, views, now);

    List<Long> pageIds =
        ranked.stream().skip((long) page * size).limit(size).map(FeedCandidate::postId).toList();
    boolean hasNext = ranked.size() > (long) (page + 1) * size;
    List<PostEntity> posts = loadInOrder(pageIds);

    Map<Long, FollowReason> reasonById = new HashMap<>();
    for (PostEntity p : posts) {
      p.getTags().stream()
          .filter(t -> reader.interest().containsKey(t.toLowerCase(Locale.ROOT)))
          .findFirst()
          .ifPresent(t -> reasonById.put(p.getId(), FollowReason.topic(t)));
    }
    List<PublicFeedItem> items =
        feedItemAssembler.assemble(posts).stream()
            .map(it -> it.withFollowReason(reasonById.get(it.id())))
            .toList();
    return new PublicFeedView(items, page, size, hasNext);
  }

  private Map<Long, FeedCandidate> candidatesById(List<Long> postIds) {
    if (postIds.isEmpty()) {
      return Map.of();
    }
    return postRepository.findAllByIdIn(postIds).stream()
        .collect(
            Collectors.toMap(
                PostEntity::getId,
                p ->
                    new FeedCandidate(
                        p.getId(),
                        p.getUserId(),
                        p.getTags(),
                        p.getLanguageTag(),
                        p.getPublishedAt(),
                        p.getSeriesId())));
  }

  private List<PostEntity> loadInOrder(List<Long> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    Map<Long, PostEntity> byId =
        postRepository.findAllByIdIn(ids).stream()
            .collect(Collectors.toMap(PostEntity::getId, Function.identity()));
    return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
  }
}
