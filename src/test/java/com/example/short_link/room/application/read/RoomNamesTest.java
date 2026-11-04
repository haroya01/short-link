package com.example.short_link.room.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.short_link.room.domain.RoomEntity;
import com.example.short_link.room.domain.repository.RoomRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoomNamesTest {

  @Mock private RoomRepository rooms;

  @Test
  void aRoomIsNamedAsItsMembersNamedItAndAGoneRoomIsNot() {
    when(rooms.findById(3L)).thenReturn(Optional.of(new RoomEntity(7L, "도쿄 모임")));
    when(rooms.findById(4L)).thenReturn(Optional.empty());

    RoomNames names = new RoomNames(rooms);

    assertThat(names.nameOf(3L)).contains("도쿄 모임");
    assertThat(names.nameOf(4L)).isEmpty();
  }
}
