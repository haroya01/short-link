package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.common.image.ImageVariantKeys;
import com.example.short_link.common.image.ImageVariantSource;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class PostBodyImageVariantSource implements ImageVariantSource {

  private final JdbcTemplate jdbc;

  @Override
  public Page after(long cursor, int limit) {
    List<String> keys = new ArrayList<>();
    long[] last = {cursor};
    jdbc.query(
        "SELECT id, content FROM post_block WHERE id > ? ORDER BY id LIMIT ?",
        row -> {
          last[0] = row.getLong("id");
          keys.addAll(ImageVariantKeys.keysIn(row.getString("content")));
        },
        cursor,
        limit);
    return new Page(last[0], keys);
  }

  @Override
  public List<String> newest(int limit) {
    List<String> keys = new ArrayList<>();
    jdbc.query(
        "SELECT content FROM post_block ORDER BY id DESC LIMIT ?",
        row -> {
          keys.addAll(ImageVariantKeys.keysIn(row.getString("content")));
        },
        limit);
    return keys;
  }
}
