package com.example.short_link.common.image;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ImageVariantLedger {

  public enum Outcome {
    MADE,
    FAILED
  }

  private final JdbcTemplate jdbc;
  private final Clock clock;

  public boolean recorded(String key) {
    return !jdbc.queryForList(
            "SELECT 1 FROM image_variant WHERE storage_key = ?", Integer.class, key)
        .isEmpty();
  }

  public List<String> unrecorded(Collection<String> keys) {
    if (keys.isEmpty()) {
      return List.of();
    }
    List<String> distinct = List.copyOf(new LinkedHashSet<>(keys));
    String marks = String.join(",", Collections.nCopies(distinct.size(), "?"));
    Set<String> recorded =
        new HashSet<>(
            jdbc.queryForList(
                "SELECT storage_key FROM image_variant WHERE storage_key IN (" + marks + ")",
                String.class,
                distinct.toArray()));
    return distinct.stream().filter(key -> !recorded.contains(key)).toList();
  }

  public void record(String key, Outcome outcome) {
    jdbc.update(
        "INSERT INTO image_variant (storage_key, outcome, created_at) VALUES (?, ?, ?) AS fresh"
            + " ON DUPLICATE KEY UPDATE outcome = fresh.outcome, created_at = fresh.created_at",
        key,
        outcome.name(),
        Timestamp.from(clock.instant()));
  }
}
