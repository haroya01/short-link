package com.example.short_link.federation.domain.repository;

import com.example.short_link.federation.domain.SeenViewer;
import java.time.Instant;
import java.util.List;

public interface RemoteDwellRepository {

  record Dwell(SeenViewer viewer, Long noteId, int ms, Instant at) {}

  // A longer dwell replaces a shorter one; a shorter one changes nothing.
  void record(List<Dwell> dwells);

  int deleteBefore(Instant before, int limit);
}
