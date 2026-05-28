package com.example.short_link.post.application.write;

import java.util.List;

/** Replace the ordered membership of a series with exactly {@code postIds} (0-based order). */
public record SetSeriesPostsCommand(Long userId, Long seriesId, List<Long> postIds) {

  public SetSeriesPostsCommand {
    if (userId == null) throw new IllegalArgumentException("userId required");
    if (seriesId == null) throw new IllegalArgumentException("seriesId required");
    postIds = postIds == null ? List.of() : List.copyOf(postIds);
    if (postIds.size() != postIds.stream().distinct().count()) {
      throw new IllegalArgumentException("postIds must be unique");
    }
  }
}
