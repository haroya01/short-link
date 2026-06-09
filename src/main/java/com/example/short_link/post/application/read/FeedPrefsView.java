package com.example.short_link.post.application.read;

import java.util.Set;

/**
 * A user's feed preferences — currently the blog-home tab that opens by default. {@code defaultTab}
 * is one of {@link #ALLOWED}; {@link #DEFAULT} ("recent") when the user hasn't chosen.
 */
public record FeedPrefsView(String defaultTab) {

  public static final String DEFAULT = "recent";
  public static final Set<String> ALLOWED = Set.of("recent", "trending", "following", "series");

  /** True when {@code tab} is a tab the reader can land on by default. */
  public static boolean isAllowed(String tab) {
    return tab != null && ALLOWED.contains(tab);
  }
}
