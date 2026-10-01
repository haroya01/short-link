package com.example.short_link.post.domain.feed;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class InterestProfile {

  public static final int MAX_TAGS = 12;

  // An explicit tag follow outweighs an incidental read.
  public static final int FOLLOWED_WEIGHT = 3;

  private InterestProfile() {}

  public static List<String> topTags(
      Collection<String> followedTags,
      Collection<? extends Collection<String>> signalPostTags,
      Collection<String> hiddenTags) {
    Map<String, Integer> weight = new HashMap<>();
    for (String tag : followedTags) {
      weight.merge(normalize(tag), FOLLOWED_WEIGHT, Integer::sum);
    }
    for (Collection<String> tags : signalPostTags) {
      for (String tag : tags) {
        weight.merge(normalize(tag), 1, Integer::sum);
      }
    }
    for (String tag : hiddenTags) {
      weight.remove(normalize(tag));
    }
    return weight.entrySet().stream()
        .sorted(
            Map.Entry.<String, Integer>comparingByValue()
                .reversed()
                .thenComparing(Map.Entry.comparingByKey()))
        .limit(MAX_TAGS)
        .map(Map.Entry::getKey)
        .toList();
  }

  static String normalize(String tag) {
    return tag.toLowerCase(Locale.ROOT);
  }
}
