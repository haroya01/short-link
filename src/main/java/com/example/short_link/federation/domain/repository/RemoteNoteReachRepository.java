package com.example.short_link.federation.domain.repository;

import com.example.short_link.federation.domain.foryou.ReachTarget;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface RemoteNoteReachRepository {

  record Counts(long likes, long reposts, long replies) {

    public static final Counts NONE = new Counts(0, 0, 0);
  }

  record Reach(Long noteId, Counts counts, boolean gone) {}

  record Due(Long noteId, String host, String statusId, int checks, int failures) {}

  record Counted(Long noteId, Counts counts, Instant at, Instant nextCheckAt, int checks) {}

  record Postponed(Long noteId, Instant nextCheckAt, int failures, Instant goneAt) {}

  // A note already tracked keeps its row.
  void track(Long noteId, ReachTarget target, Counts counts, Instant at, Instant nextCheckAt);

  Map<Long, Reach> of(Collection<Long> noteIds);

  Map<String, Long> idsByUri(Collection<String> uris);

  // Raises the counts to what a trending list reported, never lowers them.
  void trended(Map<Long, Counts> counts, Instant at);

  List<Due> due(Instant from, Instant to, int limit);

  void counted(List<Counted> rows);

  void postponed(List<Postponed> rows);
}
