package com.example.short_link.post.domain.feed.replay;

import com.example.short_link.post.domain.feed.FeedCandidate;
import com.example.short_link.post.domain.feed.FeedRanking;
import com.example.short_link.post.domain.feed.ForYouRanking;
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

  static final Ranker TRENDING_V0 =
      new Ranker(
          "trending-v0",
          context -> ids(FeedRanking.trending(context.poolNewestFirst(), context.recentViews())));

  static final Ranker TRENDING_V1 =
      new Ranker(
          "trending-v1",
          context ->
              ids(FeedRanking.trending(context.poolNewestFirst(), context.recentHumanViews())));

  // For You before v1: tag filter, newest first, and the trending SQL (bots counted) on a cold
  // start. FeedRankingSqlParityIntegrationTest pins the core trending to that SQL.
  static final Ranker FOR_YOU_V0 = new Ranker("for-you-v0", ReplayRankers::forYouV0);

  static final Ranker FOR_YOU_V1 = forYouV1("for-you-v1", ForYouRanking.Weights.DEFAULT);

  static final List<Ranker> PRODUCTION =
      List.of(RANDOM, RECENT, TRENDING_V0, TRENDING_V1, FOR_YOU_V0, FOR_YOU_V1);

  private ReplayRankers() {}

  static Ranker forYouV1(String name, ForYouRanking.Weights weights) {
    return new Ranker(
        name,
        context -> {
          Viewer viewer = context.viewer();
          ForYouRanking.Reader reader =
              ForYouRanking.Reader.of(
                  viewer.id(),
                  viewer.locale(),
                  viewer.followedTags(),
                  viewer.hiddenTags(),
                  viewer.readsNewestFirst(),
                  viewer.likesNewestFirst(),
                  context.catalog());
          return ids(
              ForYouRanking.rank(
                  context.poolNewestFirst(),
                  reader,
                  context.recentHumanViews(),
                  context.now(),
                  weights));
        });
  }

  private static List<Long> forYouV0(Context context) {
    Viewer viewer = context.viewer();
    List<List<String>> signalTags =
        ForYouV0.signalPostIds(viewer.readsNewestFirst(), viewer.likesNewestFirst()).stream()
            .map(id -> context.catalog().get(id).tags())
            .toList();
    List<String> interest =
        ForYouV0.topTags(viewer.followedTags(), signalTags, viewer.hiddenTags());
    if (interest.isEmpty()) {
      return ids(FeedRanking.trending(context.poolNewestFirst(), context.recentViews()));
    }
    return ids(
        ForYouV0.rank(context.poolNewestFirst(), viewer.id(), interest, viewer.readsNewestFirst()));
  }

  private static List<Long> ids(List<FeedCandidate> ranked) {
    return ranked.stream().map(FeedCandidate::postId).toList();
  }
}
