package com.example.short_link.common.user;

/**
 * Neutral write port for purging a user's slice-local rows ahead of the user row's hard delete. The
 * user slice cannot reach into other slices' repositories without creating dependency cycles (post
 * and profile already depend on user), so each slice that stores user-owned rows without an ON
 * DELETE CASCADE foreign key implements this port and the user slice consumes them as a collection
 * — mirroring how {@code common.post.PublishedPostCountReader} keeps the slice graph acyclic.
 * Implementations run inside the caller's transaction and must delete in an order that satisfies
 * their own FK graph.
 */
public interface UserDataEraser {

  void eraseFor(long userId);
}
