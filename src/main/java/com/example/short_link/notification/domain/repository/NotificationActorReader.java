package com.example.short_link.notification.domain.repository;

import com.example.short_link.notification.domain.NotificationActor;
import java.util.Collection;
import java.util.Map;

/**
 * Read port for resolving notification actors' display identity. Lives in the notification module
 * as a consumer port; the adapter reads the user module's {@code users} table by id, so the slice
 * never compile-depends on the user module. Resolved in a single batch per page to avoid an N+1.
 */
public interface NotificationActorReader {

  /** Identity for each given user id; ids with no (or a deleted) user are simply absent. */
  Map<Long, NotificationActor> resolve(Collection<Long> userIds);
}
