package com.example.short_link.federation.domain.repository;

import java.time.Instant;
import java.util.Map;

public interface FederationRelayedNoteRepository {

  void mark(Long noteId, Long relayId, Instant receivedAt);

  long receivedSince(Instant since);

  // Kept notes by relay id; 0 holds those whose relay is unsubscribed.
  Map<Long, Long> countByRelay();

  // Deletes up to limit notes received before the time that no member has touched, oldest first,
  // and returns how many went.
  int deleteExpired(Instant receivedBefore, int limit);
}
