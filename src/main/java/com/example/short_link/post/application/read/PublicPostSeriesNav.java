package com.example.short_link.post.application.read;

/**
 * Series context shown on a public post: which series it belongs to, its 1-based position, and the
 * adjacent published posts (null at the ends). Only published siblings are considered.
 */
public record PublicPostSeriesNav(
    String slug, String title, int position, int total, NavLink prev, NavLink next) {

  public record NavLink(String slug, String title) {}
}
