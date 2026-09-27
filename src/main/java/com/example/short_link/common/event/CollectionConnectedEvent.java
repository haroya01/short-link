package com.example.short_link.common.event;

import java.time.Instant;
import java.util.List;

// Published after a new collection connection commits; reconnecting an existing block emits
// nothing. Used only for notifications. The producer resolves recipients before publishing.
public record CollectionConnectedEvent(
    Long actorUserId,
    Long collectionId,
    String collectionName,
    Long connectedPostId,
    Long connectedAuthorUserId,
    List<Long> priorContributorUserIds,
    Instant occurredAt) {

  public CollectionConnectedEvent {
    priorContributorUserIds =
        priorContributorUserIds == null ? List.of() : List.copyOf(priorContributorUserIds);
  }
}
