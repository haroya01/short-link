package com.example.short_link.federation.domain.foryou;

import java.util.Map;
import java.util.Set;

public record ForYouReader(
    Set<String> languages, Map<String, Double> tags, Map<String, Double> authors) {

  public static final ForYouReader NONE = new ForYouReader(Set.of(), Map.of(), Map.of());

  public ForYouReader {
    languages = Set.copyOf(languages);
    tags = Map.copyOf(tags);
    authors = Map.copyOf(authors);
  }
}
