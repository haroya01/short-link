package com.example.short_link.common.post;

/**
 * Neutral read port exposing an author's published-post count to other slices (e.g. the public
 * profile's blog entry-point flag) without a profile→post type dependency. Mirrors how {@code
 * common.cache.ProfileCacheInvalidator} lets the post slice evict the profile cache without a
 * post→profile dependency — keeping the slice graph acyclic (ArchUnit-enforced). The post slice
 * provides the implementation.
 */
public interface PublishedPostCountReader {

  /** Count of the user's PUBLISHED posts (0 when they have no public blog). */
  long countPublishedByUserId(Long userId);
}
