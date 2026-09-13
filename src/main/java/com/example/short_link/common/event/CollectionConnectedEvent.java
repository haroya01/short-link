package com.example.short_link.common.event;

import java.time.Instant;
import java.util.List;

/**
 * Published after a new collection connection commits; reconnecting an existing block emits
 * nothing. Used only for notifications. The producer resolves recipients before publishing.
 *
 * @param actorUserId connecting curator; never notified
 * @param collectionId deep-link target for both notification kinds
 * @param collectionName title snapshot for notification text
 * @param connectedPostId connected post or a highlight's parent post; null for a note
 * @param connectedAuthorUserId CONNECTED recipient; null for a note or the curator's own work
 * @param priorContributorUserIds PATH_GREW recipients: distinct prior block authors, excluding the
 *     connected author and curator; notes contribute no recipient
 */
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
