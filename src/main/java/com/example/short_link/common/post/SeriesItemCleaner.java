package com.example.short_link.common.post;

// A series lists notes by id without a foreign key, so deleting a note must drop its place in the
// series in the same transaction. This port avoids a dependency from notes on the post slice.
public interface SeriesItemCleaner {

  void purgeForNote(long noteId);
}
