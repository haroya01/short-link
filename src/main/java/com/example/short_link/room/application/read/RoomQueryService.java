package com.example.short_link.room.application.read;

import com.example.short_link.room.domain.RoomEntity;
import com.example.short_link.room.domain.RoomInviteEntity;
import com.example.short_link.room.domain.RoomInviteUnusable;
import com.example.short_link.room.domain.RoomJoinRequest;
import com.example.short_link.room.domain.RoomPerson;
import com.example.short_link.room.domain.repository.RoomRepository;
import com.example.short_link.room.exception.RoomErrorCode;
import com.example.short_link.room.exception.RoomException;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoomQueryService {

  static final int LANDING_FACES = 8;
  static final int SUGGESTED_FRIENDS = 3;

  private final RoomRepository rooms;
  private final RoomInviteUrls urls;
  private final Clock clock;

  @Transactional(readOnly = true)
  public List<RoomListItem> mine(Long userId) {
    return rooms.summaries(userId).stream().map(RoomListItem::of).toList();
  }

  @Transactional(readOnly = true)
  public RoomView detail(Long roomId, Long userId) {
    RoomEntity room = joined(roomId, userId);
    List<RoomPerson> members = rooms.members(roomId, RoomEntity.MAX_MEMBERS);
    return new RoomView(
        room.getId(),
        room.getName(),
        RoomRole.of(room.isOwnedBy(userId)),
        room.isApprovalRequired(),
        rooms.hiddenFromFeed(roomId, userId),
        members.size(),
        members.stream()
            .map(person -> RoomView.Member.of(person, room.isOwnedBy(person.id())))
            .toList());
  }

  // The owner sees every invite of the room, a member only their own.
  @Transactional(readOnly = true)
  public List<RoomInviteView> invites(Long roomId, Long userId) {
    RoomEntity room = joined(roomId, userId);
    List<RoomInviteEntity> invites =
        rooms.invites(roomId).stream()
            .filter(invite -> room.isOwnedBy(userId) || invite.getCreatorId().equals(userId))
            .toList();
    List<RoomPerson> members = rooms.members(roomId, RoomEntity.MAX_MEMBERS);
    return invites.stream()
        .map(
            invite ->
                RoomInviteView.of(
                    invite,
                    urls,
                    members.stream()
                        .filter(person -> person.id().equals(invite.getCreatorId()))
                        .findFirst()
                        .orElse(null)))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<RoomJoinRequest> requests(Long roomId, Long userId) {
    RoomEntity room = joined(roomId, userId);
    if (!room.isOwnedBy(userId)) {
      throw new RoomException(RoomErrorCode.ROOM_OWNER_ONLY);
    }
    return rooms.requests(roomId);
  }

  // What anyone holding the code may see before joining: the room's name and faces, never a note.
  @Transactional(readOnly = true)
  public RoomInviteLanding landing(String code) {
    RoomInviteEntity invite =
        rooms
            .inviteByCode(code)
            .orElseThrow(() -> new RoomException(RoomErrorCode.ROOM_INVITE_NOT_FOUND));
    RoomEntity room =
        rooms
            .findById(invite.getRoomId())
            .orElseThrow(() -> new RoomException(RoomErrorCode.ROOM_INVITE_NOT_FOUND));
    long memberCount = rooms.countMembers(room.getId());
    Optional<RoomInviteUnusable> unusable =
        invite
            .unusableAt(clock.instant())
            .or(
                () ->
                    memberCount >= RoomEntity.MAX_MEMBERS
                        ? Optional.of(RoomInviteUnusable.FULL)
                        : Optional.empty());
    return new RoomInviteLanding(
        room.getName(),
        rooms.person(invite.getCreatorId()).orElse(null),
        memberCount,
        rooms.members(room.getId(), LANDING_FACES),
        invite.isApproval() || room.isApprovalRequired(),
        unusable.isEmpty(),
        unusable.map(RoomInviteUnusable::apiName).orElse(null));
  }

  @Transactional(readOnly = true)
  public RoomSuggestions suggestions(Long userId) {
    return new RoomSuggestions(rooms.friendsWithoutARoom(userId, SUGGESTED_FRIENDS));
  }

  private RoomEntity joined(Long roomId, Long userId) {
    return rooms
        .findById(roomId)
        .filter(room -> rooms.isMember(roomId, userId))
        .orElseThrow(() -> new RoomException(RoomErrorCode.ROOM_NOT_FOUND, roomId));
  }
}
