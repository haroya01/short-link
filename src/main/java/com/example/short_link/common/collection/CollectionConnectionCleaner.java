package com.example.short_link.common.collection;

import java.util.Collection;

/**
 * Polymorphic collection references have no foreign key, so callers must purge them before deleting
 * their target. Implementations run inside the caller's transaction; this port avoids a dependency
 * cycle with the collection slice.
 */
public interface CollectionConnectionCleaner {

  void purgeForPost(long postId);

  /** No-op when the collection is empty. */
  void purgeForHighlights(Collection<Long> highlightIds);

  void purgeForNote(long noteId);
}
