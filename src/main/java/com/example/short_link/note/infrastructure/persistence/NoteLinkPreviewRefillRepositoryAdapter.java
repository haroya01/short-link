package com.example.short_link.note.infrastructure.persistence;

import com.example.short_link.note.domain.repository.NoteLinkPreviewRefillRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class NoteLinkPreviewRefillRepositoryAdapter implements NoteLinkPreviewRefillRepository {

  private static final String CANDIDATES =
      " FROM note n WHERE n.body LIKE '%://%' AND n.quoted_post_id IS NULL"
          + " AND n.quoted_note_id IS NULL AND n.poll_options IS NULL"
          + " AND NOT EXISTS (SELECT 1 FROM note_link_preview p WHERE p.note_id = n.id)"
          + " AND NOT EXISTS (SELECT 1 FROM note_media m WHERE m.note_id = n.id)";

  private final JdbcTemplate jdbc;

  @Override
  public Optional<Refill> pending() {
    return jdbc
        .query(
            "SELECT name, started_at, cursor_id, targets, links_reopened, filled, missed"
                + " FROM note_link_preview_refill WHERE finished_at IS NULL ORDER BY name LIMIT 1",
            (row, i) ->
                new Refill(
                    row.getString("name"),
                    row.getTimestamp("started_at") != null,
                    row.getObject("cursor_id") == null ? Long.MAX_VALUE : row.getLong("cursor_id"),
                    row.getLong("targets"),
                    row.getInt("links_reopened"),
                    row.getInt("filled"),
                    row.getInt("missed")))
        .stream()
        .findFirst();
  }

  @Override
  public long countCandidates() {
    return jdbc.queryForObject("SELECT COUNT(*)" + CANDIDATES, Long.class);
  }

  @Override
  public boolean start(String name, long targets, int linksReopened, Instant at) {
    return jdbc.update(
            "UPDATE note_link_preview_refill SET started_at = ?, targets = ?, links_reopened = ?"
                + " WHERE name = ? AND started_at IS NULL",
            Timestamp.from(at),
            targets,
            linksReopened,
            name)
        == 1;
  }

  @Override
  public Optional<Candidate> newestBelow(long id) {
    return jdbc
        .query(
            "SELECT n.id, n.body" + CANDIDATES + " AND n.id < ? ORDER BY n.id DESC LIMIT 1",
            (row, i) -> new Candidate(row.getLong("id"), row.getString("body")),
            id)
        .stream()
        .findFirst();
  }

  @Override
  public void advance(Refill refill) {
    jdbc.update(
        "UPDATE note_link_preview_refill SET cursor_id = ?, filled = ?, missed = ? WHERE name = ?",
        refill.cursor(),
        refill.filled(),
        refill.missed(),
        refill.name());
  }

  @Override
  public void finish(String name, Instant at) {
    jdbc.update(
        "UPDATE note_link_preview_refill SET finished_at = ? WHERE name = ?",
        Timestamp.from(at),
        name);
  }
}
