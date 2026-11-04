package com.example.short_link.common.page;

import java.util.List;
import java.util.function.Function;

public record Keyset<T>(List<T> rows, String next) {

  public static <T> Keyset<T> of(List<T> fetched, int limit, Function<T, Long> key) {
    if (fetched.size() <= limit) {
      return new Keyset<>(fetched, null);
    }
    List<T> rows = fetched.subList(0, limit);
    return new Keyset<>(rows, String.valueOf(key.apply(rows.getLast())));
  }

  public static int limit(Integer asked, int fallback, int max) {
    return asked == null ? fallback : Math.clamp(asked, 1, max);
  }
}
