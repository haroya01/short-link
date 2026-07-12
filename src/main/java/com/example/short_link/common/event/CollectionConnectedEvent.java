package com.example.short_link.common.event;

import java.time.Instant;
import java.util.List;

/**
 * Published after a block (post / highlight) is newly woven into a collection commits — the connect
 * verb at the heart of the reading graph. Notification-only, and a 1-to-many fan-out concern, so
 * it's its own event rather than a {@link BlogInteractionEvent}. Fired only on a genuine new
 * connection: re-connecting an already-present block is idempotent and publishes nothing.
 *
 * <p>The producer (the collection slice, which owns the post/highlight/note repositories) resolves
 * every recipient up front so the notification listener stays a thin fan-out with no post→data
 * lookup: {@code connectedAuthorUserId} is the woven work's author (the CONNECTED recipient), and
 * {@code priorContributorUserIds} are the distinct authors of the blocks already in the collection
 * (the PATH_GREW recipients) — already deduped and already excluding both the woven author and the
 * connecting curator. A connected note carries no author here (a note's author is its curator, so
 * CONNECTED would be a self-notice) and never contributes a PATH_GREW recipient.
 *
 * @param actorUserId the curator who connected the block (the notification actor; never notified)
 * @param collectionId the collection/path that grew — the deep-link target of both notifications
 * @param collectionName collection title snapshot, for the bell text
 * @param connectedPostId the post occasioning the connection (the post itself, or the post a
 *     connected highlight sits on); null for a connected note
 * @param connectedAuthorUserId the woven work's author — the CONNECTED recipient; null when the
 *     connected block has no distinct author to notice (a note, or the curator's own work)
 * @param priorContributorUserIds distinct authors already in the collection, minus the woven author
 *     and the curator — the PATH_GREW recipients; empty when the curator is weaving into an empty
 *     or solo-authored path
 * @param occurredAt when the connection happened
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
