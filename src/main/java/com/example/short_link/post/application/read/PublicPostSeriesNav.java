package com.example.short_link.post.application.read;

/** Position is 1-based among published siblings. Adjacent links are null at either end. */
public record PublicPostSeriesNav(
    String slug, String title, int position, int total, NavLink prev, NavLink next) {

  public record NavLink(String slug, String title) {}
}
