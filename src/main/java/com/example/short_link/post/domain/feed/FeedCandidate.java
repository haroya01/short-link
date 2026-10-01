package com.example.short_link.post.domain.feed;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public record FeedCandidate(
    long postId,
    long authorId,
    List<String> tags,
    String languageTag,
    Instant publishedAt,
    Long seriesId) {

  public FeedCandidate {
    tags = List.copyOf(tags);
  }

  public Set<String> normalizedTags() {
    return tags.stream().map(t -> t.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
  }
}
