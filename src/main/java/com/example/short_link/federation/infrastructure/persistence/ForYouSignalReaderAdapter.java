package com.example.short_link.federation.infrastructure.persistence;

import static com.example.short_link.federation.infrastructure.persistence.UtcColumns.utc;

import com.example.short_link.federation.domain.SeenViewer;
import com.example.short_link.federation.domain.repository.ForYouSignalReader;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

// Tags are joined with a space, which a hashtag cannot hold.
@Repository
@RequiredArgsConstructor
class ForYouSignalReaderAdapter implements ForYouSignalReader {

  private static final String DWELT =
      "(SELECT d.note_id, 4 AS kind FROM remote_dwell d WHERE d.viewer_kind = ?"
          + " AND d.viewer_key = ? AND d.dwell_ms >= ? AND d.dwelt_at >= ?"
          + " ORDER BY d.dwelt_at DESC LIMIT ?)";

  private static final String MEMBER =
      "(SELECT l.note_id, 0 AS kind FROM note_like l WHERE l.user_id = ? AND l.created_at >= ?"
          + " ORDER BY l.id DESC LIMIT ?)"
          + " UNION ALL (SELECT b.note_id, 1 FROM note_bookmark b WHERE b.user_id = ?"
          + " AND b.created_at >= ? ORDER BY b.id DESC LIMIT ?)"
          + " UNION ALL (SELECT a.in_reply_to_id, 2 FROM note a WHERE a.user_id = ?"
          + " AND a.in_reply_to_id IS NOT NULL AND a.created_at >= ? ORDER BY a.id DESC LIMIT ?)"
          + " UNION ALL (SELECT f.id, 3 FROM federation_following ff"
          + " JOIN note f ON f.remote_actor_id = ff.remote_actor_id WHERE ff.user_id = ?"
          + " AND ff.accepted_at IS NOT NULL AND f.created_at >= ? ORDER BY f.id DESC LIMIT ?)"
          + " UNION ALL ";

  private final JdbcTemplate jdbcTemplate;

  @Override
  public Optional<String> locale(long userId) {
    return jdbcTemplate
        .queryForList("SELECT locale FROM users WHERE id = ?", String.class, userId)
        .stream()
        .findFirst();
  }

  @Override
  public List<Signal> signals(SeenViewer viewer, Instant since, int perKind, int minDwellMs) {
    boolean member = viewer.kind() == SeenViewer.MEMBER;
    LocalDateTime from = utc(since);
    List<Object> args = new ArrayList<>();
    if (member) {
      long userId = viewer.key().getLeastSignificantBits();
      for (int kind = 0; kind < 4; kind++) {
        args.addAll(List.of(userId, from, perKind));
      }
    }
    args.addAll(List.of(viewer.kind(), viewer.keyBytes(), minDwellMs, from, perKind));
    return jdbcTemplate.query(
        "SELECT s.kind, n.user_id, n.remote_actor_id, n.language,"
            + " (SELECT GROUP_CONCAT(g.tag SEPARATOR ' ') FROM note_tag g WHERE g.note_id = n.id)"
            + " FROM ("
            + (member ? MEMBER : "")
            + DWELT
            + ") s JOIN note n ON n.id = s.note_id",
        (row, at) -> {
          long userId = row.getLong(2);
          String author = row.wasNull() ? "r" + row.getLong(3) : "u" + userId;
          String tags = row.getString(5);
          return new Signal(
              Kind.values()[row.getInt(1)],
              author,
              row.getString(4),
              tags == null ? List.of() : Arrays.asList(tags.split(" ")));
        },
        args.toArray());
  }

  @Override
  public Set<Long> followed(long userId) {
    return new HashSet<>(
        jdbcTemplate.queryForList(
            "SELECT remote_actor_id FROM federation_following"
                + " WHERE user_id = ? AND accepted_at IS NOT NULL",
            Long.class,
            userId));
  }
}
