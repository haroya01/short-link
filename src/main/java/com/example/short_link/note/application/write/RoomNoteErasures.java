package com.example.short_link.note.application.write;

import com.example.short_link.note.domain.RoomErasure;
import com.example.short_link.note.domain.repository.RoomNoteErasureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// Each tick takes the oldest requests and one chunk of each, every chunk in its own transaction, so
// a
// member with thousands of room notes never holds one long transaction or starves the others.
@Service
@RequiredArgsConstructor
public class RoomNoteErasures {

  static final int REQUESTS_PER_TICK = 10;
  static final int CHUNK = 50;

  private final RoomNoteErasureRepository erasures;
  private final NoteCommandService command;

  public int eraseDue() {
    int erased = 0;
    for (RoomErasure request : erasures.pendingRoomErasures(REQUESTS_PER_TICK)) {
      erased += command.eraseRoomNotes(request.userId(), request.roomId(), CHUNK);
    }
    return erased;
  }
}
