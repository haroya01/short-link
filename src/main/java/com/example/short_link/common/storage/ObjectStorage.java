package com.example.short_link.common.storage;

import java.time.Duration;
import java.util.Optional;

/** Callers own key naming, public URLs, ownership checks, and size policy. */
public interface ObjectStorage {

  /** Configured & usable. When false, callers should refuse with a 503-type response upstream. */
  boolean isConfigured();

  /** Throws on adapter failure. */
  String presignPut(String key, String contentType, Duration ttl);

  /** Uploads server-held bytes. Throws on adapter failure. */
  void putObject(String key, String contentType, byte[] body);

  /** Return the object's size in bytes, or empty if it doesn't exist / lookup failed. */
  Optional<Long> objectSize(String key);

  /**
   * Call after validating a presigned upload. UUID keys are never rewritten, so immutable caching
   * is safe. Best-effort: logs failures without throwing.
   */
  void applyImmutableCacheControl(String key);

  /** Throws on adapter failure; the caller decides whether to propagate it. */
  void delete(String key);
}
