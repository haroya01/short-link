package com.example.short_link.post.application.read;

// position, total, prev and next walk the published posts alone, as clients that predate notes in a
// series read them; the item fields walk posts and notes together.
public record PublicPostSeriesNav(
    String slug,
    String title,
    int position,
    int total,
    NavLink prev,
    NavLink next,
    int itemPosition,
    int itemTotal,
    ItemLink prevItem,
    ItemLink nextItem) {

  public record NavLink(String slug, String title) {}

  // A post is reached by its slug, a note by its id.
  public record ItemLink(String type, String slug, Long noteId, String title) {}
}
