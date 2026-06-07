package com.example.short_link.notification.domain.repository;

import java.util.List;

/**
 * Read port for an author's follower ids, used to fan out a NEW_POST notice. Lives in the
 * notification module as a consumer port; the adapter reads the user module's {@code user_follow}
 * table directly (native query), so the slice never compile-depends on the user module — the same
 * pattern as {@link NotificationActorReader}.
 */
public interface NotificationFollowerReader {

  /** Ids of every user who follows {@code authorUserId}. Empty when the author has no followers. */
  List<Long> followerIdsOf(Long authorUserId);
}
