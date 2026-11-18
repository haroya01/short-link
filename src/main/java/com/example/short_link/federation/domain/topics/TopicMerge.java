package com.example.short_link.federation.domain.topics;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TopicMerge {

  public static final int MIN_PEOPLE = 3;

  private TopicMerge() {}

  public static List<Topic> merge(List<TopicSighting> sightings) {
    Map<String, Seen> byKey = new LinkedHashMap<>();
    for (TopicSighting sighting : sightings) {
      Seen seen = byKey.get(sighting.key());
      if (seen == null) {
        byKey.put(sighting.key(), new Seen(sighting));
      } else {
        seen.alsoAt(sighting);
      }
    }
    return sameTitlesAsOne(byKey.values()).stream()
        .filter(seen -> seen.people >= MIN_PEOPLE)
        .map(Seen::topic)
        .toList();
  }

  private static List<Seen> sameTitlesAsOne(Iterable<Seen> all) {
    List<Seen> merged = new ArrayList<>();
    Map<String, Seen> byTitle = new LinkedHashMap<>();
    for (Seen seen : all) {
      if (seen.first.tag() != null) {
        merged.add(seen);
        continue;
      }
      String title = normalized(seen.first.article().title());
      Seen same = byTitle.get(title);
      if (same == null) {
        byTitle.put(title, seen);
        merged.add(seen);
      } else if (same.people >= seen.people) {
        same.absorb(seen);
      } else {
        seen.absorb(same);
        byTitle.put(title, seen);
        merged.set(merged.indexOf(same), seen);
      }
    }
    return merged;
  }

  static String normalized(String title) {
    return Normalizer.normalize(String.valueOf(title), Normalizer.Form.NFKC)
        .toLowerCase(Locale.ROOT)
        .replaceAll("\\s+", " ")
        .strip();
  }

  private static final class Seen {

    private final TopicSighting first;
    private final Map<String, Integer> places = new LinkedHashMap<>();
    private String language;
    private int people;

    Seen(TopicSighting sighting) {
      this.first = sighting;
      alsoAt(sighting);
    }

    void alsoAt(TopicSighting sighting) {
      places.merge(sighting.host(), sighting.place(), Math::min);
      people = Math.max(people, sighting.people());
      language = language == null ? sighting.language() : language;
    }

    void absorb(Seen other) {
      other.places.forEach((host, place) -> places.merge(host, place, Math::min));
      people = Math.max(people, other.people);
      language = language == null ? other.language : language;
    }

    Topic topic() {
      List<String> servers =
          places.entrySet().stream()
              .sorted(Comparator.comparingInt(Map.Entry::getValue))
              .map(Map.Entry::getKey)
              .toList();
      int best = places.values().stream().mapToInt(Integer::intValue).min().orElse(0);
      return new Topic(first.tag(), first.article(), language, people, best, servers, List.of());
    }
  }
}
