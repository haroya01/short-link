package com.example.short_link.post.domain;

/**
 * Sort dimension for the per-post performance table. Only post-column metrics are sortable so each
 * page stays a bounded DB query — follows live in another table (user_follow) and would need the
 * whole set in memory to order by, which defeats the pagination, so they aren't a sort option.
 */
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
      return valueOf(value.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      return VIEWS;
    }
  }
}
