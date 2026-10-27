package com.example.short_link.federation.infrastructure.persistence;

import com.example.short_link.common.seen.SeenItem;
import com.example.short_link.common.seen.SeenViewer;
import com.example.short_link.federation.domain.repository.LocalSeenRepository;
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
class LocalSeenRepositoryAdapter implements LocalSeenRepository {

  private final JdbcTemplate jdbcTemplate;

  @Override
  public void insert(List<Sighting> sightings) {
    if (sightings.isEmpty()) {
      return;
    }
    List<Object> args = new ArrayList<>(sightings.size() * 5);
    for (Sighting sighting : sightings) {
      args.add(sighting.viewer().kind());
      args.add(sighting.viewer().keyBytes());
      args.add(sighting.item().kind());
      args.add(sighting.item().id());
      args.add(UtcColumns.utc(sighting.seenAt()));
    }
    jdbcTemplate.update(
        "INSERT INTO local_seen (viewer_kind, viewer_key, item_kind, item_id, seen_at) VALUES "
            + String.join(", ", Collections.nCopies(sightings.size(), "(?, ?, ?, ?, ?)"))
            + " ON DUPLICATE KEY UPDATE seen_at = seen_at",
        args.toArray());
  }

  @Override
  public Set<SeenItem> seenBefore(SeenViewer viewer, Collection<SeenItem> items, Instant before) {
    if (items.isEmpty()) {
      return Set.of();
    }
    List<Object> args = new ArrayList<>(items.size() * 2 + 3);
    args.add(viewer.kind());
    args.add(viewer.keyBytes());
    items.forEach(
        item -> {
          args.add(item.kind());
          args.add(item.id());
        });
    args.add(UtcColumns.utc(before));
    return new HashSet<>(
        jdbcTemplate.query(
            "SELECT item_kind, item_id FROM local_seen WHERE viewer_kind = ? AND viewer_key = ?"
                + " AND (item_kind, item_id) IN ("
                + String.join(", ", Collections.nCopies(items.size(), "(?, ?)"))
                + ") AND seen_at < ?",
            (row, at) -> new SeenItem(row.getInt(1), row.getLong(2)),
            args.toArray()));
  }

  @Override
  public int deleteBefore(Instant before, int limit) {
    return jdbcTemplate.update(
        "DELETE FROM local_seen WHERE seen_at < ? LIMIT ?", UtcColumns.utc(before), limit);
  }
}
