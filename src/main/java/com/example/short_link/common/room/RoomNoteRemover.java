package com.example.short_link.common.room;

// The note slice implements this so a member leaving a room can take their notes with them without
// the room slice importing it. Asking returns at once; the notes go in the background.
public interface RoomNoteRemover {

  void eraseNotesBy(Long userId, Long roomId);
}
