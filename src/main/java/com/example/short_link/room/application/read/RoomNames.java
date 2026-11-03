package com.example.short_link.room.application.read;

import com.example.short_link.common.room.RoomNameReader;
import com.example.short_link.room.domain.RoomEntity;
import com.example.short_link.room.domain.repository.RoomRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class RoomNames implements RoomNameReader {

  private final RoomRepository rooms;

  @Override
  public Optional<String> nameOf(Long roomId) {
    return rooms.findById(roomId).map(RoomEntity::getName);
  }
}
