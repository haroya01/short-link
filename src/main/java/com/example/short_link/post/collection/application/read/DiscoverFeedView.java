package com.example.short_link.post.collection.application.read;

import java.util.List;

/** {@code source} is "following" or "global"; global covers fallback and explicit scope. */
public record DiscoverFeedView(
    List<DiscoverConnectionView> items, int page, int size, boolean hasNext, String source) {

  public static final String SOURCE_FOLLOWING = "following";
  public static final String SOURCE_GLOBAL = "global";
}
