package com.example.short_link.note.application.write;

import com.example.short_link.note.domain.RoomErasure;
import com.example.short_link.note.domain.repository.NoteRepository;
import com.example.short_link.note.domain.repository.RoomNoteErasureRepository;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

// Every chunk runs in its own transaction, so no erasure holds one long transaction or starves
// the others.
@Service
@RequiredArgsConstructor
public class RoomNoteErasures {

  static final int REQUESTS_PER_TICK = 10;
  static final int CHUNK = 50;

  private final RoomNoteErasureRepository erasures;
  private final NoteRepository notes;
  private final NoteThreadControls threads;
  private final TransactionTemplate transaction;
  private final Clock clock;

  @Transactional
  public void request(Long userId, Long roomId) {
    erasures.requestRoomErasure(userId, roomId, clock.instant().truncatedTo(ChronoUnit.MICROS));
  }

  public int eraseDue() {
    int erased = 0;
    for (RoomErasure request : erasures.pendingRoomErasures(REQUESTS_PER_TICK)) {
      erased += transaction.execute(status -> erase(request.userId(), request.roomId(), CHUNK));
    }
    return erased;
  }

  // Running it again after a crash only finds what is left.
  int erase(Long userId, Long roomId, int chunk) {
    List<Long> ids = erasures.roomNoteIdsBy(userId, roomId, chunk);
    notes.findAllByIdIn(ids).forEach(threads::remove);
    if (ids.size() < chunk) {
      erasures.finishRoomErasure(userId, roomId);
    }
    return ids.size();
  }
}
