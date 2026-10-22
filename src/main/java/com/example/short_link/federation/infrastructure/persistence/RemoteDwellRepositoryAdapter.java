package com.example.short_link.federation.infrastructure.persistence;

import static com.example.short_link.federation.infrastructure.persistence.UtcColumns.utc;

import com.example.short_link.federation.domain.repository.RemoteDwellRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

// IGNORE skips a note deleted since its uri was looked up instead of failing the whole statement on
// its foreign key.
@Repository
@RequiredArgsConstructor
class RemoteDwellRepositoryAdapter implements RemoteDwellRepository {

  private final JdbcTemplate jdbcTemplate;

  @Override
  public void record(List<Dwell> dwells) {
    if (dwells.isEmpty()) {
      return;
    }
    List<Object> args = new ArrayList<>(dwells.size() * 5);
    for (Dwell dwell : dwells) {
      args.add(dwell.viewer().kind());
      args.add(dwell.viewer().keyBytes());
      args.add(dwell.noteId());
      args.add(dwell.ms());
      args.add(utc(dwell.at()));
    }
    jdbcTemplate.update(
        "INSERT IGNORE INTO remote_dwell (viewer_kind, viewer_key, note_id, dwell_ms, dwelt_at)"
            + " VALUES "
            + String.join(", ", Collections.nCopies(dwells.size(), "(?, ?, ?, ?, ?)"))
            + " AS sent ON DUPLICATE KEY UPDATE"
            + " dwelt_at = IF(sent.dwell_ms > remote_dwell.dwell_ms, sent.dwelt_at,"
            + " remote_dwell.dwelt_at),"
            + " dwell_ms = GREATEST(remote_dwell.dwell_ms, sent.dwell_ms)",
        args.toArray());
  }

  @Override
  public int deleteBefore(Instant before, int limit) {
    return jdbcTemplate.update(
        "DELETE FROM remote_dwell WHERE dwelt_at < ? LIMIT ?", utc(before), limit);
  }
}
