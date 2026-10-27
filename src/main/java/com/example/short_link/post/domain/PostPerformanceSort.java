package com.example.short_link.post.domain;

import java.util.Locale;

public enum PostPerformanceSort {
  VIEWS,
  LIKES,
  RECENT;

  public static PostPerformanceSort fromParam(String value) {
    if (value == null) {
      return VIEWS;
    }
    try {
      return valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      return VIEWS;
    }
  }
}
