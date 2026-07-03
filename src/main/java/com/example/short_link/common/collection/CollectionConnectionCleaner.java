package com.example.short_link.common.collection;

import java.util.Collection;

/**
 * Neutral write port for purging the polymorphic {@code collection_connection} rows that point at a
 * block (post / highlight / note) about to be hard-deleted. A connection's {@code ref_id} is a
 * polymorphic reference with no foreign key (three target tables can't share one FK), so nothing at
 * the DB level clears it when the target disappears — the count stays overstated while the detail
 * view silently skips the dead ref. The collection slice already depends on post and note (it reads
 * those blocks to resolve connections), so the delete paths can't reach back into the collection
 * repository without a cycle; they consume this port and the collection slice provides the
 * implementation — mirroring {@code common.user.UserDataEraser} and {@code
 * common.post.PublishedPostCountReader}. Implementations run inside the caller's transaction.
 */
public interface CollectionConnectionCleaner {

  /** Drop connections pointing at this post (block_type = POST). */
  void purgeForPost(long postId);

  /** Drop connections pointing at these highlights (block_type = HIGHLIGHT). No-op when empty. */
  void purgeForHighlights(Collection<Long> highlightIds);

  /** Drop connections pointing at this note (block_type = NOTE). */
  void purgeForNote(long noteId);
}
