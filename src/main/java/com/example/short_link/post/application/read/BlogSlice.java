package com.example.short_link.post.application.read;

import java.time.Instant;
import java.util.List;

public record BlogSlice(
    List<PostCard> posts,
    Instant firstAt,
    Long firstId,
    Instant lastAt,
    Long lastId,
    boolean more) {

  public static final BlogSlice EMPTY = new BlogSlice(List.of(), null, null, null, null, false);
}
