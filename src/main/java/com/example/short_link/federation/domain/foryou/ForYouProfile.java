package com.example.short_link.federation.domain.foryou;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ForYouProfile {

  public static final double LIKED = 1.0;
  public static final double BOOKMARKED = 1.5;
  public static final double ANSWERED = 1.5;
  public static final double DWELT = 0.5;
  public static final double FOLLOWED = 1.0;

  public static final double LANGUAGE_SHARE = 0.25;
  public static final int LANGUAGE_MIN_SIGNALS = 2;
  public static final int ACCEPTED_LANGUAGES = 2;

  public record Signal(double weight, String author, String language, Collection<String> tags) {}

  private ForYouProfile() {}

  public static ForYouReader of(Collection<String> languages, List<Signal> signals) {
    Set<String> known = new LinkedHashSet<>();
    languages.stream()
        .map(ForYouProfile::language)
        .filter(lang -> lang != null)
        .forEach(known::add);
    Map<String, Double> tags = new HashMap<>();
    Map<String, Double> authors = new HashMap<>();
    Map<String, Integer> read = new HashMap<>();
    for (Signal signal : signals) {
      ForYouCandidate.normalized(signal.tags())
          .forEach(tag -> tags.merge(tag, signal.weight(), Double::sum));
      if (signal.author() != null) {
        authors.merge(signal.author(), signal.weight(), Double::sum);
      }
      String lang = language(signal.language());
      if (lang != null) {
        read.merge(lang, 1, Integer::sum);
      }
    }
    read.forEach(
        (lang, count) -> {
          if (count >= LANGUAGE_MIN_SIGNALS && count >= LANGUAGE_SHARE * signals.size()) {
            known.add(lang);
          }
        });
    return new ForYouReader(known, tags, authors);
  }

  public static String language(String tag) {
    if (tag == null || tag.isBlank()) {
      return null;
    }
    String primary = tag.strip().split("[-_;]", 2)[0].toLowerCase(Locale.ROOT);
    return primary.isEmpty() || primary.equals("*") ? null : primary;
  }

  public static List<String> accepted(String header) {
    if (header == null || header.isBlank()) {
      return List.of();
    }
    Set<String> languages = new LinkedHashSet<>();
    for (String range : header.split(",")) {
      String lang = language(range);
      if (lang != null) {
        languages.add(lang);
      }
      if (languages.size() == ACCEPTED_LANGUAGES) {
        break;
      }
    }
    return List.copyOf(languages);
  }
}
