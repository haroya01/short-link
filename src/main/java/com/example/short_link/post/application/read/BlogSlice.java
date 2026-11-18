package com.example.short_link.post.application.read;

import java.time.Instant;
import java.util.List;

public record BlogSlice(List<PostCard> posts, Place first, Place last, boolean more) {

  public static final BlogSlice EMPTY = new BlogSlice(List.of(), null, null, false);

  public record Place(Instant publishedAt, long id, boolean rest) {}
}
