package com.example.short_link.link.application;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * Opaque pagination cursor — encodes the (createdAt, id) pair of the last row from the previous
 * page so the next page can resume strictly after it. Descending sort: a row qualifies when {@code
 * createdAt < cursor.createdAt} OR (equal createdAt AND {@code id < cursor.id}). The id tie-break
 * is what keeps two links created in the same millisecond from skipping or duplicating across
 * pages.
 *
 * <p>Wire format: base64-url(no-pad) of {@code "<createdAtMillis>:<id>"}. Opaque to clients;
 * malformed input throws {@link IllegalArgumentException} which the controller maps to 400.
 */
public record MyLinksCursor(Instant createdAt, long id) {

  public String encode() {
    long micros = createdAt.getEpochSecond() * 1_000_000L + createdAt.getNano() / 1000L;
    String raw = micros + ":" + id;
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  public static MyLinksCursor decode(String encoded) {
    if (encoded == null || encoded.isBlank()) return null;
    try {
      String raw = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
      int sep = raw.indexOf(':');
      if (sep <= 0 || sep == raw.length() - 1) {
        throw new IllegalArgumentException("cursor malformed");
      }
      long micros = Long.parseLong(raw.substring(0, sep));
      long id = Long.parseLong(raw.substring(sep + 1));
      Instant createdAt =
          Instant.ofEpochSecond(
              Math.floorDiv(micros, 1_000_000L), Math.floorMod(micros, 1_000_000L) * 1000L);
      return new MyLinksCursor(createdAt, id);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("cursor malformed");
    }
  }
}
