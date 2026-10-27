package com.example.short_link.note.application.write;

import com.example.short_link.common.room.RoomNoteRemover;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class RoomNotesByWriter implements RoomNoteRemover {

  private final RoomNoteErasures erasures;

  @Override
  public void eraseNotesBy(Long userId, Long roomId) {
    erasures.request(userId, roomId);
  }
}
