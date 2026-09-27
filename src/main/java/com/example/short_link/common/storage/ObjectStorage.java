package com.example.short_link.common.storage;

import java.time.Duration;
import java.util.Optional;

public interface ObjectStorage {

  boolean isConfigured();

  String presignPut(String key, String contentType, Duration ttl);

  void putObject(String key, String contentType, byte[] body);

  Optional<Long> objectSize(String key);

  // Call after validating a presigned upload. UUID keys are never rewritten, so immutable caching
  // is safe. Best-effort: logs failures without throwing.
  void applyImmutableCacheControl(String key);

  void delete(String key);
}
