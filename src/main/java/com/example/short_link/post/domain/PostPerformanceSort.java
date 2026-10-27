package com.example.short_link.post.domain;

import java.util.Locale;

// 키는 posts 의 열이라 DB 가 이 순서로 페이지를 나눈다. 다른 표에 있는 수(팔로우)는 키가 될 수 없다.
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
