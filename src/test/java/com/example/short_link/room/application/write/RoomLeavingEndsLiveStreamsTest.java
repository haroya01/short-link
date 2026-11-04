package com.example.short_link.room.application.write;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.live.LiveRevoke;
import com.example.short_link.common.room.RoomNoteRemover;
import com.example.short_link.common.room.RoomPostRemover;
import com.example.short_link.room.application.read.RoomQueryService;
import com.example.short_link.room.domain.RoomEntity;
import com.example.short_link.room.domain.repository.RoomInvitationRepository;
import com.example.short_link.room.domain.repository.RoomInviteRepository;
import com.example.short_link.room.domain.repository.RoomRepository;
import com.example.short_link.room.domain.repository.RoomSuggestionRepository;
import com.example.short_link.room.exception.RoomException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class RoomLeavingEndsLiveStreamsTest {

  private final RoomRepository rooms = mock(RoomRepository.class);
  private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  private final RoomCommandService command =
      new RoomCommandService(
          rooms,
          mock(RoomInviteRepository.class),
          mock(RoomInvitationRepository.class),
          mock(RoomSuggestionRepository.class),
          new RoomWriteGuard(rooms),
          mock(RoomQueryService.class),
          mock(RoomNoteRemover.class),
          mock(RoomPostRemover.class),
          Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneOffset.UTC),
          events);

  @Test
  void aMemberWhoLeavesStopsWatchingTheRoom() {
    when(rooms.lock(4L)).thenReturn(Optional.of(new RoomEntity(7L, "방")));
    when(rooms.isMember(4L, 2L)).thenReturn(true);

    command.leave(2L, 4L, false);

    verify(events).publishEvent(new LiveRevoke.LeftRoom(4L, 2L));
  }

  @Test
  void aMemberTheOwnerRemovesStopsWatchingTheRoom() {
    when(rooms.lock(4L)).thenReturn(Optional.of(new RoomEntity(7L, "방")));
    when(rooms.isMember(4L, 7L)).thenReturn(true);
    when(rooms.isMember(4L, 2L)).thenReturn(true);

    command.remove(7L, 4L, 2L);

    verify(events).publishEvent(new LiveRevoke.LeftRoom(4L, 2L));
  }

  @Test
  void someoneWhoWasNeverInTheRoomEndsNothing() {
    when(rooms.lock(4L)).thenReturn(Optional.of(new RoomEntity(7L, "방")));
    when(rooms.isMember(4L, 2L)).thenReturn(false);

    Assertions.assertThatThrownBy(() -> command.leave(2L, 4L, false))
        .isInstanceOf(RoomException.class);
    verify(events, never()).publishEvent(any());
  }
}
