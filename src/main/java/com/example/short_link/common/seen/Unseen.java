package com.example.short_link.common.seen;

import jakarta.persistence.Query;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

public record Unseen(
    int viewerKind, UUID viewerKey, Instant asOf, List<Long> heldNotes, List<Long> heldPosts) {

  public static final int NOTE = 1;
  public static final int POST = 2;

  public Unseen {
    heldNotes = heldNotes.isEmpty() ? List.of(-1L) : List.copyOf(heldNotes);
    heldPosts = heldPosts.isEmpty() ? List.of(-1L) : List.copyOf(heldPosts);
  }

  // The writer's own rows stay; :viewer is the member id, or -1 for a device.
  public static String sql(String alias, int kind) {
    return " AND ("
        + alias
        + ".user_id = :viewer OR (NOT EXISTS (SELECT 1 FROM local_seen ls"
        + " WHERE ls.viewer_kind = :seenKind AND ls.viewer_key = :seenKey AND ls.item_kind = "
        + kind
        + " AND ls.item_id = "
        + alias
        + ".id AND ls.seen_at < :seenAsOf) AND "
        + alias
        + ".id NOT IN (:seenHeld)))";
  }

  public void bind(Query query, int kind) {
    query
        .setParameter("seenKind", viewerKind)
        .setParameter(
            "seenKey",
            ByteBuffer.allocate(16)
                .putLong(viewerKey.getMostSignificantBits())
                .putLong(viewerKey.getLeastSignificantBits())
                .array())
        .setParameter("seenAsOf", LocalDateTime.ofInstant(asOf, ZoneOffset.UTC))
        .setParameter("seenHeld", kind == NOTE ? heldNotes : heldPosts);
  }
}
