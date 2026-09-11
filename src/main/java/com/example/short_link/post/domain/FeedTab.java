package com.example.short_link.post.domain;

import java.util.Set;

/** 피드의 기본 진입 탭으로 선택 가능한 값. */
public final class FeedTab {
  public static final String DEFAULT = "recent";
  private static final Set<String> ALLOWED = Set.of("recent", "trending", "following", "series");

  private FeedTab() {}

  public static boolean isAllowed(String tab) {
    return tab != null && ALLOWED.contains(tab);
  }
}
