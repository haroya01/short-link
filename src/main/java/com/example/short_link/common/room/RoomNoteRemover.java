package com.example.short_link.common.room;

// The note slice implements this so a member leaving a room can take their notes with them without
// the room slice importing it.
public interface RoomNoteRemover {

  int removeNotesBy(Long userId, Long roomId);
}
