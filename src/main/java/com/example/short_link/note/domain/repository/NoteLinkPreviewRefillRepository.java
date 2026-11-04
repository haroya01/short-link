package com.example.short_link.note.domain.repository;

import java.time.Instant;
import java.util.Optional;

public interface NoteLinkPreviewRefillRepository {

  Optional<Refill> pending();

  long countCandidates();

  boolean start(String name, long targets, int linksReopened, Instant at);

  Optional<Candidate> newestBelow(long id);

  void advance(Refill refill);

  void finish(String name, Instant at);

  record Candidate(long id, String body) {}

  record Refill(
      String name,
      boolean started,
      long cursor,
      long targets,
      int linksReopened,
      int filled,
      int missed) {

    public Refill begun(long targets, int linksReopened) {
      return new Refill(name, true, cursor, targets, linksReopened, filled, missed);
    }

    public Refill skipped(long noteId) {
      return new Refill(name, started, noteId, targets, linksReopened, filled, missed);
    }

    public Refill refreshed(long noteId, boolean cardStored) {
      return new Refill(
          name,
          started,
          noteId,
          targets,
          linksReopened,
          cardStored ? filled + 1 : filled,
          cardStored ? missed : missed + 1);
    }
  }
}
