package com.example.short_link.post.application.read;

public record PublicPostSeriesNav(
    String slug, String title, int position, int total, NavLink prev, NavLink next) {

  public record NavLink(String slug, String title) {}
}
