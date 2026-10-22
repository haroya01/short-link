package com.example.short_link.federation.domain.foryou;

import java.time.Instant;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public record ForYouCandidate(
    Object ref,
    String uri,
    String author,
    String host,
    String language,
    Set<String> tags,
    Instant createdAt,
    long likes,
    long reposts,
    long replies,
    long order) {

  public ForYouCandidate {
    author = author == null ? uri : author;
    host = host == null ? "" : host;
    language = ForYouProfile.language(language);
    tags = normalized(tags);
  }

  public long engagement() {
    return Math.max(0, likes) + 2 * Math.max(0, reposts) + Math.max(0, replies);
  }

  static Set<String> normalized(Collection<String> tags) {
    return tags == null
        ? Set.of()
        : tags.stream().map(tag -> tag.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
  }
}
