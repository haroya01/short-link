package com.example.short_link.federation.infrastructure.persistence;

import static com.example.short_link.federation.infrastructure.persistence.UtcColumns.utc;

import com.example.short_link.federation.domain.foryou.ReachTarget;
import com.example.short_link.federation.domain.repository.RemoteNoteReachRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

// IGNORE skips a note deleted since it was read instead of failing the whole statement on its
// foreign key.
@Repository
@RequiredArgsConstructor
class RemoteNoteReachRepositoryAdapter implements RemoteNoteReachRepository {

  private final JdbcTemplate jdbcTemplate;

  @Override
  public void track(
      Long noteId, ReachTarget target, Counts counts, Instant at, Instant nextCheckAt) {
    jdbcTemplate.update(
        "INSERT INTO remote_note_reach (note_id, ask_host, ask_id, likes, reposts, replies,"
            + " counted_at, next_check_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
            + " ON DUPLICATE KEY UPDATE note_id = note_id",
        noteId,
        target == null ? null : target.host(),
        target == null ? null : target.id(),
        counts.likes(),
        counts.reposts(),
        counts.replies(),
        utc(at),
        target == null ? null : utc(nextCheckAt));
  }

  @Override
  public Map<Long, Reach> of(Collection<Long> noteIds) {
    if (noteIds.isEmpty()) {
      return Map.of();
    }
    Map<Long, Reach> reach = new HashMap<>();
    jdbcTemplate.query(
        "SELECT note_id, likes, reposts, replies, gone_at FROM remote_note_reach WHERE note_id IN ("
            + marks(noteIds.size())
            + ")",
        row -> {
          long id = row.getLong(1);
          reach.put(
              id,
              new Reach(
                  id,
                  new Counts(row.getLong(2), row.getLong(3), row.getLong(4)),
                  row.getTimestamp(5) != null));
        },
        noteIds.toArray());
    return reach;
  }

  @Override
  public Map<String, Long> idsByUri(Collection<String> uris) {
    if (uris.isEmpty()) {
      return Map.of();
    }
    Map<String, Long> ids = new HashMap<>();
    jdbcTemplate.query(
        "SELECT id, uri FROM note WHERE uri IN (" + marks(uris.size()) + ")",
        row -> {
          ids.put(row.getString(2), row.getLong(1));
        },
        uris.toArray());
    return ids;
  }

  @Override
  public void trended(Map<Long, Counts> counts, Instant at) {
    if (counts.isEmpty()) {
      return;
    }
    List<Object> args = new ArrayList<>(counts.size() * 6);
    counts.forEach(
        (noteId, count) -> {
          args.add(noteId);
          args.add(count.likes());
          args.add(count.reposts());
          args.add(count.replies());
          args.add(utc(at));
          args.add(utc(at));
        });
    jdbcTemplate.update(
        "INSERT IGNORE INTO remote_note_reach (note_id, likes, reposts, replies, counted_at,"
            + " trending_at)"
            + " VALUES "
            + String.join(", ", Collections.nCopies(counts.size(), "(?, ?, ?, ?, ?, ?)"))
            + " AS listed ON DUPLICATE KEY UPDATE"
            + " likes = GREATEST(remote_note_reach.likes, listed.likes),"
            + " reposts = GREATEST(remote_note_reach.reposts, listed.reposts),"
            + " replies = GREATEST(remote_note_reach.replies, listed.replies),"
            + " counted_at = listed.counted_at, trending_at = listed.trending_at",
        args.toArray());
  }

  @Override
  public List<Due> due(Instant from, Instant to, int limit) {
    return jdbcTemplate.query(
        "SELECT note_id, ask_host, ask_id, checks, failures FROM remote_note_reach"
            + " WHERE next_check_at >= ? AND next_check_at <= ? AND ask_host IS NOT NULL"
            + " ORDER BY next_check_at LIMIT ?",
        (row, at) ->
            new Due(
                row.getLong(1), row.getString(2), row.getString(3), row.getInt(4), row.getInt(5)),
        utc(from),
        utc(to),
        limit);
  }

  @Override
  public void counted(List<Counted> rows) {
    if (rows.isEmpty()) {
      return;
    }
    List<Object> args = new ArrayList<>(rows.size() * 7);
    for (Counted row : rows) {
      args.add(row.noteId());
      args.add(row.counts().likes());
      args.add(row.counts().reposts());
      args.add(row.counts().replies());
      args.add(utc(row.at()));
      args.add(utc(row.nextCheckAt()));
      args.add(row.checks());
    }
    jdbcTemplate.update(
        "INSERT IGNORE INTO remote_note_reach (note_id, likes, reposts, replies, counted_at,"
            + " next_check_at, checks) VALUES "
            + String.join(", ", Collections.nCopies(rows.size(), "(?, ?, ?, ?, ?, ?, ?)"))
            + " AS asked ON DUPLICATE KEY UPDATE likes = asked.likes, reposts = asked.reposts,"
            + " replies = asked.replies, counted_at = asked.counted_at,"
            + " next_check_at = asked.next_check_at, checks = asked.checks, failures = 0",
        args.toArray());
  }

  @Override
  public void postponed(List<Postponed> rows) {
    if (rows.isEmpty()) {
      return;
    }
    List<Object> args = new ArrayList<>(rows.size() * 4);
    for (Postponed row : rows) {
      args.add(row.noteId());
      args.add(utc(row.nextCheckAt()));
      args.add(row.failures());
      args.add(utc(row.goneAt()));
    }
    jdbcTemplate.update(
        "INSERT IGNORE INTO remote_note_reach (note_id, next_check_at, failures, gone_at) VALUES "
            + String.join(", ", Collections.nCopies(rows.size(), "(?, ?, ?, ?)"))
            + " AS asked ON DUPLICATE KEY UPDATE next_check_at = asked.next_check_at,"
            + " failures = asked.failures,"
            + " gone_at = COALESCE(asked.gone_at, remote_note_reach.gone_at)",
        args.toArray());
  }

  private static String marks(int count) {
    return String.join(", ", Collections.nCopies(count, "?"));
  }
}
