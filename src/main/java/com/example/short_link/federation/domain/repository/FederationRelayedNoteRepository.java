package com.example.short_link.federation.domain.repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface FederationRelayedNoteRepository {

  void mark(Long noteId, Long relayId, Instant receivedAt);

  void markPulled(Long noteId, String host, Instant receivedAt);

  long relayedSince(Instant since);

  long pulledSince(Instant since);

  // When the newest note pulled from the server was published, if any is kept.
  Optional<Instant> newestPulled(String host);

  // Notes relays brought, by relay id; 0 holds those whose relay is unsubscribed.
  Map<Long, Long> countByRelay();

  // Up to limit notes received before the time that no member has touched, oldest first.
  List<Long> expired(Instant receivedBefore, int limit);

  // Deletes those of the notes no member has touched by now and returns how many went.
  int deleteUntouched(List<Long> noteIds);
}
