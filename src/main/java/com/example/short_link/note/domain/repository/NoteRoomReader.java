package com.example.short_link.note.domain.repository;

import java.util.Collection;
import java.util.Map;

public interface NoteRoomReader {

  boolean isMember(Long roomId, Long userId);

  Map<Long, String> names(Collection<Long> roomIds);
}
