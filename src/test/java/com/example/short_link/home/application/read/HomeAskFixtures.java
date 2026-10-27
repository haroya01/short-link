package com.example.short_link.home.application.read;

import com.example.short_link.common.seen.SeenViewer;

public final class HomeAskFixtures {

  private HomeAskFixtures() {}

  public static HomeAsk ask(
      Long viewerId,
      SeenViewer viewer,
      String tab,
      String cursor,
      String since,
      int limit,
      boolean hideSeen) {
    return new HomeAsk(
        viewerId, viewer, null, HomeFeedQueryService.Tab.of(tab), cursor, since, limit, hideSeen);
  }
}
