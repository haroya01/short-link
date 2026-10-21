package com.example.short_link.post.application.write;

import com.example.short_link.post.domain.repository.RoomPostErasureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// Each tick takes the oldest requests and one chunk of each, every chunk in its own transaction, as
// RoomNoteErasures does for notes.
@Service
@RequiredArgsConstructor
public class RoomPostErasures {

  static final int REQUESTS_PER_TICK = 10;
  static final int CHUNK = 20;

  private final RoomPostErasureRepository erasures;
  private final RoomPostEraser eraser;

  public int eraseDue() {
    int erased = 0;
    for (RoomPostErasureRepository.Request request : erasures.pending(REQUESTS_PER_TICK)) {
      erased += eraser.eraseChunk(request.userId(), request.roomId(), CHUNK);
    }
    return erased;
  }
}
