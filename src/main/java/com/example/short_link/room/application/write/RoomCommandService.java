package com.example.short_link.room.application.write;

import com.example.short_link.common.room.RoomNoteRemover;
import com.example.short_link.common.room.RoomPostRemover;
import com.example.short_link.room.application.read.RoomQueryService;
import com.example.short_link.room.application.read.RoomView;
import com.example.short_link.room.domain.RoomEntity;
import com.example.short_link.room.domain.repository.RoomInvitationRepository;
import com.example.short_link.room.domain.repository.RoomInviteRepository;
import com.example.short_link.room.domain.repository.RoomRepository;
import com.example.short_link.room.domain.repository.RoomSuggestionRepository;
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
  private final RoomInviteRepository invites;
  private final RoomInvitationRepository invitations;
  private final RoomSuggestionRepository suggestions;
  private final RoomWriteGuard guard;
  private final RoomQueryService query;
  private final RoomNoteRemover notes;
  private final RoomPostRemover posts;
  private final Clock clock;

  @Transactional
  public RoomView create(Long userId, String rawName) {
    RoomEntity room = rooms.save(new RoomEntity(userId, name(rawName)));
    rooms.addMember(room.getId(), userId, now());
    return query.detail(userId, room.getId());
  }

  @Transactional
  public RoomView update(Long userId, Long roomId, String rawName, Boolean approvalRequired) {
    RoomEntity room = guard.owned(userId, roomId);
    if (rawName != null) {
      room.rename(name(rawName));
    }
    if (approvalRequired != null) {
      room.askForApproval(approvalRequired);
    }
    return query.detail(userId, roomId);
  }

  @Transactional
  public void leave(Long userId, Long roomId, boolean deleteMyNotes) {
    RoomEntity room = guard.locked(roomId);
    guard.requireMember(userId, roomId);
    rooms.removeMember(roomId, userId);
    invites.disableInvitesBy(roomId, userId, now());
    invitations.dropInvitationsBy(roomId, userId);
    if (room.isOwnedBy(userId)) {
      rooms.longestStayingMemberBesides(roomId, userId).ifPresent(room::handTo);
    }
    if (deleteMyNotes) {
      notes.eraseNotesBy(userId, roomId);
      posts.erasePostsBy(userId, roomId);
    }
  }

  @Transactional
  public void remove(Long ownerId, Long roomId, Long memberId) {
    guard.owned(ownerId, roomId);
    if (ownerId.equals(memberId)) {
      throw new RoomException(RoomErrorCode.ROOM_OWNER_ONLY);
    }
    guard.requireMember(memberId, roomId);
    Instant now = now();
    rooms.removeMember(roomId, memberId);
    invites.disableInvitesBy(roomId, memberId, now);
    invitations.dropInvitationsBy(roomId, memberId);
    rooms.markRemoved(roomId, memberId, now);
  }

  @Transactional
  public boolean hideFromFeed(Long userId, Long roomId, boolean hidden) {
    guard.requireMember(userId, roomId);
    rooms.hideFromFeed(roomId, userId, hidden);
    return hidden;
  }

  @Transactional
  public void pin(Long userId, Long roomId, boolean pinned) {
    guard.requireMember(userId, roomId);
    if (pinned == rooms.isPinned(roomId, userId)) {
      return;
    }
    if (pinned && rooms.countPinned(userId) >= MAX_PINNED) {
      throw new RoomException(RoomErrorCode.ROOM_PINS_FULL, MAX_PINNED);
    }
    if (pinned) {
      rooms.pin(roomId, userId, now());
    } else {
      rooms.unpin(roomId, userId);
    }
  }

  @Transactional
  public long read(Long userId, Long roomId, Long noteId) {
    guard.requireMember(userId, roomId);
    rooms.readUpTo(roomId, userId, noteId);
    return rooms.unread(roomId, userId);
  }

  @Transactional
  public void dismissSuggestions(Long userId, Collection<Long> friendIds) {
    List<Long> friends = friendIds == null ? List.of() : List.copyOf(friendIds);
    if (friends.isEmpty() || friends.size() > MAX_DISMISSED_AT_ONCE) {
      throw new RoomException(RoomErrorCode.ROOM_SUGGESTION_INVALID, MAX_DISMISSED_AT_ONCE);
    }
    suggestions.dismissSuggestions(userId, friends, now());
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
