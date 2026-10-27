package com.example.short_link.room.application.write;

import com.example.short_link.room.application.write.RoomInviteService.JoinResult;
import com.example.short_link.room.application.write.RoomInviteService.Joined;
import com.example.short_link.room.domain.RoomEntity;
import com.example.short_link.room.domain.RoomInvitee;
import com.example.short_link.room.domain.repository.RoomRepository;
import com.example.short_link.room.exception.RoomErrorCode;
import com.example.short_link.room.exception.RoomException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoomInvitationService {

  static final int MAX_AT_ONCE = 20;

  private final RoomRepository rooms;
  private final RoomWriteGuard guard;
  private final Clock clock;

  // The room is locked so two members cannot both pass the waiting cap.
  @Transactional
  public List<RoomInvitee> invite(Long userId, Long roomId, Collection<Long> userIds) {
    List<Long> ids =
        userIds == null ? List.of() : userIds.stream().filter(Objects::nonNull).distinct().toList();
    if (ids.isEmpty() || ids.size() > MAX_AT_ONCE) {
      throw new RoomException(RoomErrorCode.ROOM_INVITATION_INVALID, MAX_AT_ONCE);
    }
    guard.locked(roomId);
    guard.requireMember(userId, roomId);
    if (rooms.countInvitees(roomId) + ids.size() > RoomEntity.MAX_MEMBERS) {
      throw new RoomException(RoomErrorCode.ROOM_INVITATIONS_PILED, RoomEntity.MAX_MEMBERS);
    }
    rooms.addInvitations(roomId, userId, ids, now());
    return rooms.invitees(roomId);
  }

  @Transactional
  public void cancel(Long userId, Long roomId, Long inviteeId) {
    RoomEntity room = guard.locked(roomId);
    guard.requireMember(userId, roomId);
    boolean mayCancel =
        rooms
            .inviterOf(roomId, inviteeId)
            .filter(inviter -> inviter.equals(userId) || room.isOwnedBy(userId))
            .isPresent();
    if (!mayCancel) {
      throw new RoomException(RoomErrorCode.ROOM_INVITATION_NOT_FOUND, roomId);
    }
    rooms.dropInvitation(roomId, inviteeId);
  }

  // An invitation whose sender is no longer a member is dropped, and stays dropped though the
  // answer is an error.
  @Transactional(noRollbackFor = RoomException.class)
  public JoinResult accept(Long userId, Long roomId) {
    RoomEntity room =
        rooms
            .lock(roomId)
            .orElseThrow(() -> new RoomException(RoomErrorCode.ROOM_INVITATION_NOT_FOUND, roomId));
    Long inviter =
        rooms
            .inviterOf(roomId, userId)
            .orElseThrow(() -> new RoomException(RoomErrorCode.ROOM_INVITATION_NOT_FOUND, roomId));
    if (!rooms.isMember(roomId, inviter)) {
      rooms.dropInvitation(roomId, userId);
      throw new RoomException(RoomErrorCode.ROOM_INVITATION_NOT_FOUND, roomId);
    }
    if (rooms.isMember(roomId, userId)) {
      rooms.dropInvitation(roomId, userId);
      return new JoinResult(Joined.ALREADY_MEMBER, roomId);
    }
    if (rooms.isRemoved(roomId, userId)) {
      throw new RoomException(RoomErrorCode.ROOM_REMOVED);
    }
    if (rooms.hasRequested(roomId, userId)) {
      rooms.dropInvitation(roomId, userId);
      return new JoinResult(Joined.REQUESTED, roomId);
    }
    if (rooms.countMembers(roomId) >= RoomEntity.MAX_MEMBERS) {
      throw new RoomException(RoomErrorCode.ROOM_FULL, RoomEntity.MAX_MEMBERS);
    }
    rooms.dropInvitation(roomId, userId);
    Instant now = now();
    if (room.isApprovalRequired() && !room.isOwnedBy(inviter)) {
      rooms.request(roomId, userId, null, now);
      return new JoinResult(Joined.REQUESTED, roomId);
    }
    rooms.addMember(roomId, userId, now);
    return new JoinResult(Joined.JOINED, roomId);
  }

  // A declined invitation stays on record so the same room cannot ask again.
  @Transactional
  public void decline(Long userId, Long roomId) {
    if (!rooms.declineInvitation(roomId, userId, now())) {
      throw new RoomException(RoomErrorCode.ROOM_INVITATION_NOT_FOUND, roomId);
    }
  }

  private Instant now() {
    return clock.instant().truncatedTo(ChronoUnit.MICROS);
  }
}
