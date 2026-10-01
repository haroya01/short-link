package com.example.short_link.post.domain.feed.replay;

import com.example.short_link.post.domain.feed.FeedCandidate;
import com.example.short_link.post.domain.feed.FeedRanking;
import com.example.short_link.post.domain.feed.InterestProfile;
import com.example.short_link.post.domain.feed.replay.ReplayEvaluator.Context;
import com.example.short_link.post.domain.feed.replay.ReplayEvaluator.Ranker;
import com.example.short_link.post.domain.feed.replay.ReplayEvaluator.Viewer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

final class ReplayRankers {

  static final Ranker RANDOM =
      new Ranker(
          "random",
          context -> {
            List<Long> ids = new ArrayList<>(ids(context.poolNewestFirst()));
            Collections.shuffle(ids, new Random(context.evaluation()));
            return ids;
          });

  static final Ranker RECENT =
      new Ranker("recent", context -> ids(FeedRanking.recent(context.poolNewestFirst())));

  static final Ranker TRENDING =
      new Ranker(
          "trending",
          context -> ids(FeedRanking.trending(context.poolNewestFirst(), context.recentViews())));

  // The production For You path: the same core calls, with the core trending standing in for the
  // trending SQL on a cold start (FeedRankingSqlParityIntegrationTest pins the two together).
  static final Ranker FOR_YOU_V0 = new Ranker("for-you-v0", ReplayRankers::forYouV0);

  static final List<Ranker> BASELINE = List.of(RANDOM, RECENT, TRENDING, FOR_YOU_V0);

  private ReplayRankers() {}

  private static List<Long> forYouV0(Context context) {
    Viewer viewer = context.viewer();
    List<List<String>> signalTags =
        InterestProfile.signalPostIds(viewer.readsNewestFirst(), viewer.likesNewestFirst()).stream()
            .map(context.tagsByPostId()::get)
            .toList();
    List<String> interest =
        InterestProfile.topTags(viewer.followedTags(), signalTags, viewer.hiddenTags());
    if (interest.isEmpty()) {
      return ids(FeedRanking.trending(context.poolNewestFirst(), context.recentViews()));
    }
    return ids(
        FeedRanking.forYou(
            context.poolNewestFirst(), viewer.id(), interest, viewer.readsNewestFirst()));
  }

  private static List<Long> ids(List<FeedCandidate> ranked) {
    return ranked.stream().map(FeedCandidate::postId).toList();
  }
}
