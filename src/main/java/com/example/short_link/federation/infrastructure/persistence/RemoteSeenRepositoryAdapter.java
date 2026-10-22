package com.example.short_link.federation.infrastructure.persistence;

import com.example.short_link.federation.domain.SeenUri;
import com.example.short_link.federation.domain.SeenViewer;
import com.example.short_link.federation.domain.repository.RemoteSeenRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class RemoteSeenRepositoryAdapter implements RemoteSeenRepository {

  private final JdbcTemplate jdbcTemplate;

  @Override
  public void insert(List<Sighting> sightings) {
    if (sightings.isEmpty()) {
      return;
    }
    List<Object> args = new ArrayList<>(sightings.size() * 4);
    for (Sighting sighting : sightings) {
      args.add(sighting.viewer().kind());
      args.add(sighting.viewer().keyBytes());
      args.add(sighting.uri().bytes());
      args.add(Timestamp.from(sighting.seenAt()));
    }
    jdbcTemplate.update(
        "INSERT INTO remote_seen (viewer_kind, viewer_key, uri_hash, seen_at) VALUES "
            + String.join(", ", Collections.nCopies(sightings.size(), "(?, ?, ?, ?)"))
            + " ON DUPLICATE KEY UPDATE seen_at = seen_at",
        args.toArray());
  }

  @Override
  public Set<SeenUri> seenBefore(SeenViewer viewer, Collection<SeenUri> uris, Instant before) {
    if (uris.isEmpty()) {
      return Set.of();
    }
    List<Object> args = new ArrayList<>(uris.size() + 3);
    args.add(viewer.kind());
    args.add(viewer.keyBytes());
    uris.forEach(uri -> args.add(uri.bytes()));
    args.add(Timestamp.from(before));
    return new HashSet<>(
        jdbcTemplate.query(
            "SELECT uri_hash FROM remote_seen WHERE viewer_kind = ? AND viewer_key = ?"
                + " AND uri_hash IN ("
                + String.join(", ", Collections.nCopies(uris.size(), "?"))
                + ") AND seen_at < ?",
            (row, at) -> SeenUri.fromBytes(row.getBytes(1)),
            args.toArray()));
  }

  @Override
  public int deleteBefore(Instant before, int limit) {
    return jdbcTemplate.update(
        "DELETE FROM remote_seen WHERE seen_at < ? LIMIT ?", Timestamp.from(before), limit);
  }
}
