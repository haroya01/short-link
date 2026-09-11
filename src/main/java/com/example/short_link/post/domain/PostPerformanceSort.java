package com.example.short_link.post.domain;

import java.util.Locale;

/** DB 페이지 정렬을 유지하도록 글 테이블의 지표만 허용한다. 팔로우 수는 다른 테이블이므로 제외한다. */
public enum PostPerformanceSort {
  VIEWS,
  LIKES,
  RECENT;

  /** Lenient parse for the request param — unknown/blank falls back to VIEWS. */
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
