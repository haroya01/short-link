package com.example.short_link.post.domain;

import java.util.Set;

public final class FeedTab {
  public static final String DEFAULT = "recent";
  private static final Set<String> ALLOWED = Set.of("recent", "trending", "following", "series");

  private FeedTab() {}

  public static boolean isAllowed(String tab) {
    return tab != null && ALLOWED.contains(tab);
  }
}
