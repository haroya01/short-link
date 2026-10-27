package com.example.short_link.room.domain.repository;

import com.example.short_link.room.domain.RoomPerson;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface RoomSuggestionRepository {

  // Mutual follows with a username, newest mutual first, not waved off, sharing no room with the
  // member, and no block either way.
  List<RoomPerson> friendsWithoutARoom(Long userId, int limit);

  void dismissSuggestions(Long userId, Collection<Long> friendIds, Instant at);
}
