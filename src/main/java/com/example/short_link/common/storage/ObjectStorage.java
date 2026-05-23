package com.example.short_link.common.storage;

import java.time.Duration;
import java.util.Optional;

/**
 * Thin port for object storage. Single-call semantics (presign / head / delete) — no business flow.
 * Application owns: key naming, public URL composition, ownership / size policy decisions.
 */
public interface ObjectStorage {

  /** Configured & usable. When false, callers should refuse with a 503-type response upstream. */
  boolean isConfigured();

  /** Sign a PUT URL the client can use to upload directly. Throws on adapter failure. */
  String presignPut(String key, String contentType, Duration ttl);

  /** Return the object's size in bytes, or empty if it doesn't exist / lookup failed. */
  Optional<Long> objectSize(String key);

  /** Delete the object. Throws on adapter failure — caller decides to swallow or propagate. */
  void delete(String key);
}
