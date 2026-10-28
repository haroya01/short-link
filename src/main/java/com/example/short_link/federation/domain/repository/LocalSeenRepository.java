package com.example.short_link.federation.domain.repository;

import com.example.short_link.common.seen.SeenItem;
import com.example.short_link.common.seen.SeenViewer;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface LocalSeenRepository {

  void insert(List<Sighting> sightings);

  Set<SeenItem> seenBefore(SeenViewer viewer, Collection<SeenItem> items, Instant before);

  int deleteBefore(Instant before, int limit);

  record Sighting(SeenViewer viewer, SeenItem item, Instant seenAt) {}
}
