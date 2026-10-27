package com.example.short_link.room.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.short_link.room.domain.RoomEntity;
import com.example.short_link.room.domain.repository.RoomRepository;
import com.example.short_link.room.exception.RoomErrorCode;
import com.example.short_link.room.exception.RoomException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoomWriteGuardTest {

  @Mock private RoomRepository rooms;

  private static RoomErrorCode code(Throwable e) {
    return ((RoomException) e).errorCode();
  }

  @Test
  void theOwnerPassesAMemberWhoIsNotTheOwnerDoesNotAndAStrangerSeesNoRoom() {
    RoomEntity room = new RoomEntity(7L, "책 모임");
    when(rooms.lock(30L)).thenReturn(Optional.of(room));
    when(rooms.isMember(30L, 7L)).thenReturn(true);
    when(rooms.isMember(30L, 8L)).thenReturn(true);
    when(rooms.isMember(30L, 9L)).thenReturn(false);
    RoomWriteGuard guard = new RoomWriteGuard(rooms);

    assertThat(guard.owned(7L, 30L)).isSameAs(room);
    assertThatThrownBy(() -> guard.owned(8L, 30L))
        .satisfies(e -> assertThat(code(e)).isEqualTo(RoomErrorCode.ROOM_OWNER_ONLY));
    assertThatThrownBy(() -> guard.owned(9L, 30L))
        .satisfies(e -> assertThat(code(e)).isEqualTo(RoomErrorCode.ROOM_NOT_FOUND));
  }

  @Test
  void aRoomThatIsGoneIsNotFound() {
    when(rooms.lock(31L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> new RoomWriteGuard(rooms).locked(31L))
        .satisfies(e -> assertThat(code(e)).isEqualTo(RoomErrorCode.ROOM_NOT_FOUND));
  }
}
