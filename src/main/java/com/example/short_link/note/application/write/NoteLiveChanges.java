package com.example.short_link.note.application.write;

import com.example.short_link.common.live.LiveChange;
import com.example.short_link.common.live.LiveTopic;
import java.util.Optional;

final class NoteLiveChanges {

  private NoteLiveChanges() {}

  static Optional<LiveChange> of(Long noteId, Long parentId, Long roomId, boolean direct) {
    if (parentId != null) {
      return Optional.of(
          new LiveChange(LiveTopic.note(parentId), LiveChange.Kind.REPLY, noteId, roomId, direct));
    }
    if (roomId != null) {
      return Optional.of(
          new LiveChange(LiveTopic.room(roomId), LiveChange.Kind.NOTE, noteId, roomId, direct));
    }
    return Optional.empty();
  }
}
