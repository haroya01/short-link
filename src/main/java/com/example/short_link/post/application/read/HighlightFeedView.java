package com.example.short_link.post.application.read;

import java.util.List;

public record HighlightFeedView(
    List<HighlightFeedItem> items, int page, int size, boolean hasNext, String source) {

  public static final String SOURCE_FOLLOWING = "following";
  public static final String SOURCE_GLOBAL = "global";
}
