package com.example.short_link.federation.domain.repository;

import com.example.short_link.common.seen.SeenViewer;
import com.example.short_link.federation.domain.SeenUri;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface RemoteSeenRepository {

  void insert(List<Sighting> sightings);

  Set<SeenUri> seenBefore(SeenViewer viewer, Collection<SeenUri> uris, Instant before);

  int deleteBefore(Instant before, int limit);

  record Sighting(SeenViewer viewer, SeenUri uri, Instant seenAt) {}
}
