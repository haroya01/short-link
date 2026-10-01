package com.example.short_link.post.domain.feed;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public final class InterestProfile {

  // An explicit tag follow outweighs an incidental read.
  public static final int FOLLOWED_WEIGHT = 3;

  public static final int SIGNAL_POSTS = 40;

  public static final double LANGUAGE_SHARE = 0.25;

  public static final int LANGUAGE_MIN_READS = 2;

  private InterestProfile() {}

  public static List<Long> signalPostIds(List<Long> readsNewestFirst, List<Long> likesNewestFirst) {
    return Stream.concat(
            readsNewestFirst.stream().limit(SIGNAL_POSTS),
            likesNewestFirst.stream().limit(SIGNAL_POSTS))
        .distinct()
        .toList();
  }

  public static Map<String, Integer> weights(
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
    return weight;
  }

  public static Set<String> languages(String locale, Collection<String> readLanguages) {
    Set<String> languages = new HashSet<>();
    if (locale != null) {
      languages.add(locale);
    }
    Map<String, Integer> reads = new HashMap<>();
    readLanguages.forEach(lang -> reads.merge(lang, 1, Integer::sum));
    reads.forEach(
        (lang, count) -> {
          if (count >= LANGUAGE_MIN_READS && count >= LANGUAGE_SHARE * readLanguages.size()) {
            languages.add(lang);
          }
        });
    return Set.copyOf(languages);
  }

  static String normalize(String tag) {
    return tag.toLowerCase(Locale.ROOT);
  }
}
