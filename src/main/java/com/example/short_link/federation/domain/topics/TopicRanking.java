package com.example.short_link.federation.domain.topics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TopicRanking {

  public static final int PER_LANGUAGE = 15;
  public static final int OVERALL = 40;
  public static final int MAX_LIMIT = 40;

  public static final Comparator<Topic> ORDER =
      Comparator.comparingInt(Topic::people)
          .reversed()
          .thenComparingInt(Topic::place)
          .thenComparing(Topic::id);

  private TopicRanking() {}

  public static List<Topic> pool(List<Topic> topics) {
    List<Topic> ranked = topics.stream().sorted(ORDER).toList();
    Map<String, Integer> taken = new HashMap<>();
    List<Topic> pool = new ArrayList<>();
    for (int at = 0; at < ranked.size(); at++) {
      Topic topic = ranked.get(at);
      boolean ofItsLanguage =
          topic.language() != null
              && taken.merge(topic.language(), 1, Integer::sum) <= PER_LANGUAGE;
      if (at < OVERALL || ofItsLanguage) {
        pool.add(topic);
      }
    }
    return List.copyOf(pool);
  }

  public static List<Topic> forReader(List<Topic> pool, Set<String> own, int limit) {
    List<Topic> shown = new ArrayList<>(pool.size());
    pool.stream().filter(topic -> own(topic, own)).forEach(shown::add);
    pool.stream().filter(topic -> !own(topic, own)).forEach(shown::add);
    return shown.subList(0, Math.min(Math.clamp(limit, 1, MAX_LIMIT), shown.size()));
  }

  public static boolean own(Topic topic, Set<String> own) {
    return topic.language() != null && own.contains(topic.language());
  }
}
