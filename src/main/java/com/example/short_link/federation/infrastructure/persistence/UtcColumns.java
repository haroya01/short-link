package com.example.short_link.federation.infrastructure.persistence;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

// Hibernate writes an Instant as its UTC wall time, and these columns are compared with ones it
// wrote; a java.sql.Timestamp would be shifted into the connection's time zone instead.
final class UtcColumns {

  private UtcColumns() {}

  static LocalDateTime utc(Instant at) {
    return at == null ? null : LocalDateTime.ofInstant(at, ZoneOffset.UTC);
  }
}
