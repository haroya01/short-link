package com.example.short_link.link.application.dto;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * Opaque pagination cursor — encodes the (createdAt, id) pair of the last row from the previous
 * page so the next page can resume strictly after it. The optional sortValue is used only by
 * computed server-side sorts such as clickCount, where the DB row cursor alone is not enough to
 * resume the sorted projection.
 *
 * <p>Wire format: base64-url(no-pad) of {@code "<createdAtMicros>:<id>"} for the legacy createdAt
 * cursor, or {@code "v2:<createdAtMicros>:<id>:<sortValue>"} for computed sorts. Opaque to clients;
 * malformed input throws {@link IllegalArgumentException} which the controller maps to 400.
 */
public record MyLinksCursor(Instant createdAt, long id, Long sortValue) {

  public MyLinksCursor(Instant createdAt, long id) {
    this(createdAt, id, null);
  }

  public String encode() {
    long micros = createdAt.getEpochSecond() * 1_000_000L + createdAt.getNano() / 1000L;
    String raw =
        sortValue == null ? micros + ":" + id : "v2:" + micros + ":" + id + ":" + sortValue;
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  public static MyLinksCursor decode(String encoded) {
    if (encoded == null || encoded.isBlank()) return null;
    try {
      String raw = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
      if (raw.startsWith("v2:")) {
        String[] parts = raw.split(":", -1);
        if (parts.length != 4 || parts[1].isBlank() || parts[2].isBlank() || parts[3].isBlank()) {
          throw new IllegalArgumentException("cursor malformed");
        }
        return fromMicros(
            Long.parseLong(parts[1]), Long.parseLong(parts[2]), Long.parseLong(parts[3]));
      }
      int sep = raw.indexOf(':');
      if (sep <= 0 || sep == raw.length() - 1) {
        throw new IllegalArgumentException("cursor malformed");
      }
      long micros = Long.parseLong(raw.substring(0, sep));
      long id = Long.parseLong(raw.substring(sep + 1));
      return fromMicros(micros, id, null);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("cursor malformed");
    }
  }

  private static MyLinksCursor fromMicros(long micros, long id, Long sortValue) {
    Instant createdAt =
        Instant.ofEpochSecond(
            Math.floorDiv(micros, 1_000_000L), Math.floorMod(micros, 1_000_000L) * 1000L);
    return new MyLinksCursor(createdAt, id, sortValue);
  }
}
