package com.example.short_link.note.application.live;

import com.example.short_link.common.live.LiveScope;
import com.example.short_link.common.live.LiveScopeReader;
import com.example.short_link.common.live.LiveTopic;
import com.example.short_link.note.domain.repository.NoteRoomReader;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class RoomNotesLiveScope implements LiveScopeReader {

  private final NoteRoomReader rooms;

  @Override
  public LiveTopic.Scope scope() {
    return LiveTopic.Scope.ROOM;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<LiveScope> open(long id, long viewerId, String clientIp) {
    return rooms.isMember(id, viewerId) ? Optional.of(new LiveScope(null, id)) : Optional.empty();
  }

  @Override
  @Transactional(readOnly = true)
  public long latest(long id) {
    return rooms.latestNote(id);
  }
}
