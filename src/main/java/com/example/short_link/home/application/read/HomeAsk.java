package com.example.short_link.home.application.read;

import com.example.short_link.common.seen.SeenViewer;

public record HomeAsk(
    Long viewerId,
    SeenViewer viewer,
    String languages,
    HomeFeedQueryService.Tab tab,
    String cursor,
    String since,
    int limit,
    boolean hideSeen) {

  static final int MAX_LIMIT = 40;

  int size() {
    return Math.clamp(limit, 1, MAX_LIMIT);
  }

  HomeAsk fresh(SeenViewer hideFor) {
    return new HomeAsk(viewerId, hideFor, languages, tab, null, null, limit, hideFor != null);
  }
}
