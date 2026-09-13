package com.example.short_link.post.application.read;

import java.util.List;

/**
 * {@code source} is "following" or "global". Global results come from fallback or explicit scope.
 */
public record HighlightFeedView(
    List<HighlightFeedItem> items, int page, int size, boolean hasNext, String source) {

  public static final String SOURCE_FOLLOWING = "following";
  public static final String SOURCE_GLOBAL = "global";
}
