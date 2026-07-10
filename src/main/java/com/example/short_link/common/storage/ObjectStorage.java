package com.example.short_link.common.storage;

import java.time.Duration;
import java.util.Optional;

/**
 * Thin port for object storage. Single-call semantics (presign / put / head / delete) — no business
 * flow. Application owns: key naming, public URL composition, ownership / size policy decisions.
 */
public interface ObjectStorage {

  /** Configured & usable. When false, callers should refuse with a 503-type response upstream. */
  boolean isConfigured();

  /** Sign a PUT URL the client can use to upload directly. Throws on adapter failure. */
  String presignPut(String key, String contentType, Duration ttl);

  /**
   * Upload bytes straight from the server — no presign round-trip. For server-fetched content (e.g.
   * re-hosting an external image URL) where the browser never holds the bytes. Throws on adapter
   * failure.
   */
  void putObject(String key, String contentType, byte[] body);

  /** Return the object's size in bytes, or empty if it doesn't exist / lookup failed. */
  Optional<Long> objectSize(String key);

  /**
   * Re-tag an already-uploaded object as immutable-cacheable (long-lived {@code Cache-Control}).
   * For presigned client uploads, where the PUT itself carries no cache metadata — call after
   * commit-side validation. Keys here are UUID-based and never rewritten, so "immutable" is safe.
   * Best-effort: failures are logged, never thrown — cache metadata is an optimization, not a
   * correctness requirement.
   */
  void applyImmutableCacheControl(String key);

  /** Delete the object. Throws on adapter failure — caller decides to swallow or propagate. */
  void delete(String key);
}
