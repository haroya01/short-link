package com.example.short_link.room.domain.repository;

import com.example.short_link.room.domain.RoomEntity;
import com.example.short_link.room.domain.RoomPerson;
import com.example.short_link.room.domain.RoomSummary;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface RoomRepository {

  RoomEntity save(RoomEntity room);

  Optional<RoomEntity> findById(Long roomId);

  Optional<RoomEntity> lock(Long roomId);

  boolean isMember(Long roomId, Long userId);

  long countMembers(Long roomId);

  void addMember(Long roomId, Long userId, Instant at);

  void removeMember(Long roomId, Long userId);

  List<RoomPerson> members(Long roomId, int limit);

  Optional<Long> longestStayingMemberBesides(Long roomId, Long userId);

  boolean hiddenFromFeed(Long roomId, Long userId);

  void hideFromFeed(Long roomId, Long userId, boolean hidden);

  long countPinned(Long userId);

  boolean isPinned(Long roomId, Long userId);

  void pin(Long roomId, Long userId, Instant at);

  void unpin(Long roomId, Long userId);

  void readUpTo(Long roomId, Long userId, Long noteId);

  long unread(Long roomId, Long userId);

  List<RoomSummary> summaries(Long userId);

  Map<Long, List<RoomPerson>> faces(Collection<Long> roomIds, Long besidesUserId, int perRoom);

  void markRemoved(Long roomId, Long userId, Instant at);

  boolean isRemoved(Long roomId, Long userId);
}
