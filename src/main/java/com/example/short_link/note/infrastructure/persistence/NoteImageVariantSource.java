package com.example.short_link.note.infrastructure.persistence;

import com.example.short_link.common.image.ImageVariantSource;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class NoteImageVariantSource implements ImageVariantSource {

  private final JdbcTemplate jdbc;

  @Override
  public Page after(long cursor, int limit) {
    List<String> keys = new ArrayList<>();
    long[] last = {cursor};
    jdbc.query(
        "SELECT id, storage_key FROM note_media WHERE id > ? ORDER BY id LIMIT ?",
        row -> {
          last[0] = row.getLong("id");
          keys.add(row.getString("storage_key"));
        },
        cursor,
        limit);
    return new Page(last[0], keys);
  }

  @Override
  public List<String> newest(int limit) {
    return jdbc.queryForList(
        "SELECT storage_key FROM note_media ORDER BY id DESC LIMIT ?", String.class, limit);
  }
}
