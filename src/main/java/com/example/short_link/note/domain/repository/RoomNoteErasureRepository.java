package com.example.short_link.note.domain.repository;

import com.example.short_link.note.domain.RoomErasure;
import java.time.Instant;
import java.util.List;

public interface RoomNoteErasureRepository {

  List<Long> roomNoteIdsBy(Long authorId, Long roomId, int limit);

  void requestRoomErasure(Long userId, Long roomId, Instant at);

  List<RoomErasure> pendingRoomErasures(int limit);

  void finishRoomErasure(Long userId, Long roomId);
}
