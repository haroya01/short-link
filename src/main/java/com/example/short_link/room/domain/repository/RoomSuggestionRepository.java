package com.example.short_link.room.domain.repository;

import com.example.short_link.room.domain.RoomPerson;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface RoomSuggestionRepository {

  List<RoomPerson> friendsWithoutARoom(Long userId, int limit);

  void dismissSuggestions(Long userId, Collection<Long> friendIds, Instant at);
}
