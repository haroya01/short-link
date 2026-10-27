package com.example.short_link.room.application.write;

import com.example.short_link.room.domain.RoomEntity;
import com.example.short_link.room.domain.repository.RoomRepository;
import com.example.short_link.room.exception.RoomErrorCode;
import com.example.short_link.room.exception.RoomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class RoomWriteGuard {

  private final RoomRepository rooms;

  RoomEntity locked(Long roomId) {
    return rooms
        .lock(roomId)
        .orElseThrow(() -> new RoomException(RoomErrorCode.ROOM_NOT_FOUND, roomId));
  }

  RoomEntity owned(Long userId, Long roomId) {
    RoomEntity room = locked(roomId);
    requireMember(userId, roomId);
    if (!room.isOwnedBy(userId)) {
      throw new RoomException(RoomErrorCode.ROOM_OWNER_ONLY);
    }
    return room;
  }

  void requireMember(Long userId, Long roomId) {
    if (!rooms.isMember(roomId, userId)) {
      throw new RoomException(RoomErrorCode.ROOM_NOT_FOUND, roomId);
    }
  }
}
