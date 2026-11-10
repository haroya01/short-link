package com.example.short_link.link.domain;

import com.example.short_link.support.TestEntities;
import java.time.Instant;

public final class FetchedOg {

  private FetchedOg() {}

  public static LinkEntity on(
      LinkEntity link, String title, String description, String image, Instant fetchedAt) {
    TestEntities.setField(link, "ogTitle", title);
    TestEntities.setField(link, "ogDescription", description);
    TestEntities.setField(link, "ogImage", image);
    TestEntities.setField(link, "ogFetchedAt", fetchedAt);
    TestEntities.setField(link, "ogFetchStatus", LinkEntity.ogFetchedStatus());
    TestEntities.setField(link, "ogFetchAttempts", link.getOgFetchAttempts() + 1);
    return link;
  }
}
