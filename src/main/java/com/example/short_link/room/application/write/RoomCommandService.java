package com.example.short_link.room.application.write;

import com.example.short_link.common.room.RoomNoteRemover;
import com.example.short_link.room.application.read.RoomQueryService;
import com.example.short_link.room.application.read.RoomView;
import com.example.short_link.room.domain.RoomEntity;
import com.example.short_link.room.domain.repository.RoomRepository;
import com.example.short_link.room.exception.RoomErrorCode;
import com.example.short_link.room.exception.RoomException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoomCommandService {

  static final int MAX_DISMISSED_AT_ONCE = 10;
  static final int MAX_PINNED = 5;

  private final RoomRepository rooms;
  private final RoomQueryService query;
  private final RoomNoteRemover notes;
  private final Clock clock;

  @Transactional
  public RoomView create(Long userId, String rawName) {
    RoomEntity room = rooms.save(new RoomEntity(userId, name(rawName)));
    rooms.addMember(room.getId(), userId, now());
    return query.detail(room.getId(), userId);
  }

  @Transactional
  public RoomView update(Long userId, Long roomId, String rawName, Boolean approvalRequired) {
    RoomEntity room = owned(roomId, userId);
    if (rawName != null) {
      room.rename(name(rawName));
    }
    if (approvalRequired != null) {
      room.askForApproval(approvalRequired);
    }
    return query.detail(roomId, userId);
  }

  // Leaving keeps the member's notes unless they ask otherwise; their invites stop working, and an
  // owner hands the room to whoever has been in it longest.
  @Transactional
  public void leave(Long userId, Long roomId, boolean deleteMyNotes) {
    RoomEntity room = locked(roomId);
    requireMember(roomId, userId);
    rooms.removeMember(roomId, userId);
    rooms.disableInvitesBy(roomId, userId, now());
    if (room.isOwnedBy(userId)) {
      rooms.longestStayingMemberBesides(roomId, userId).ifPresent(room::handTo);
    }
    if (deleteMyNotes) {
      notes.eraseNotesBy(userId, roomId);
    }
  }

  // The removed member's notes stay; no invite lets them back in.
  @Transactional
  public void remove(Long ownerId, Long roomId, Long memberId) {
    owned(roomId, ownerId);
    if (ownerId.equals(memberId)) {
      throw new RoomException(RoomErrorCode.ROOM_OWNER_ONLY);
    }
    requireMember(roomId, memberId);
    Instant now = now();
    rooms.removeMember(roomId, memberId);
    rooms.disableInvitesBy(roomId, memberId, now);
    rooms.markRemoved(roomId, memberId, now);
  }

  @Transactional
  public boolean hideFromFeed(Long userId, Long roomId, boolean hidden) {
    requireMember(roomId, userId);
    rooms.hideFromFeed(roomId, userId, hidden);
    return hidden;
  }

  @Transactional
  public void pin(Long userId, Long roomId, boolean pinned) {
    requireMember(roomId, userId);
    if (pinned == rooms.isPinned(roomId, userId)) {
      return;
    }
    if (pinned && rooms.countPinned(userId) >= MAX_PINNED) {
      throw new RoomException(RoomErrorCode.ROOM_PINS_FULL, MAX_PINNED);
    }
    rooms.pin(roomId, userId, pinned ? now() : null);
  }

  @Transactional
  public long read(Long userId, Long roomId, Long noteId) {
    requireMember(roomId, userId);
    rooms.readUpTo(roomId, userId, noteId);
    return rooms.unread(roomId, userId);
  }

  @Transactional
  public void dismissSuggestions(Long userId, Collection<Long> friendIds) {
    List<Long> friends = friendIds == null ? List.of() : List.copyOf(friendIds);
    if (friends.isEmpty() || friends.size() > MAX_DISMISSED_AT_ONCE) {
      throw new RoomException(RoomErrorCode.ROOM_SUGGESTION_INVALID, MAX_DISMISSED_AT_ONCE);
    }
    rooms.dismissSuggestions(userId, friends, now());
  }

  private RoomEntity owned(Long roomId, Long userId) {
    RoomEntity room = locked(roomId);
    requireMember(roomId, userId);
    if (!room.isOwnedBy(userId)) {
      throw new RoomException(RoomErrorCode.ROOM_OWNER_ONLY);
    }
    return room;
  }

  private RoomEntity locked(Long roomId) {
    return rooms
        .lock(roomId)
        .orElseThrow(() -> new RoomException(RoomErrorCode.ROOM_NOT_FOUND, roomId));
  }

  private void requireMember(Long roomId, Long userId) {
    if (!rooms.isMember(roomId, userId)) {
      throw new RoomException(RoomErrorCode.ROOM_NOT_FOUND, roomId);
    }
  }

  private static String name(String raw) {
    String name = raw == null ? "" : raw.strip();
    if (name.isEmpty() || name.codePointCount(0, name.length()) > RoomEntity.MAX_NAME_LENGTH) {
      throw new RoomException(RoomErrorCode.ROOM_NAME_INVALID, RoomEntity.MAX_NAME_LENGTH);
    }
    return name;
  }

  private Instant now() {
    return clock.instant().truncatedTo(ChronoUnit.MICROS);
  }
}
