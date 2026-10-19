package com.example.short_link.note.application.write;

import com.example.short_link.common.room.RoomNoteRemover;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class RoomNotesByWriter implements RoomNoteRemover {

  private final NoteCommandService command;

  @Override
  public int removeNotesBy(Long userId, Long roomId) {
    return command.deleteRoomNotes(userId, roomId);
  }
}
