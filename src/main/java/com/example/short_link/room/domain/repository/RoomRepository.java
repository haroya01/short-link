package com.example.short_link.room.domain.repository;

import com.example.short_link.room.domain.RoomEntity;
import com.example.short_link.room.domain.RoomInvitation;
import com.example.short_link.room.domain.RoomInviteEntity;
import com.example.short_link.room.domain.RoomInvitee;
import com.example.short_link.room.domain.RoomJoinRequest;
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

  RoomInviteEntity save(RoomInviteEntity invite);

  Optional<RoomInviteEntity> inviteByCode(String code);

  Optional<RoomInviteEntity> lockInvite(String code);

  Optional<RoomInviteEntity> invite(Long roomId, Long inviteId);

  List<RoomInviteEntity> invites(Long roomId);

  void disableInvitesBy(Long roomId, Long creatorId, Instant at);

  boolean request(Long roomId, Long userId, Long inviteId, Instant at);

  boolean hasRequested(Long roomId, Long userId);

  boolean withdrawRequest(Long roomId, Long userId);

  List<RoomJoinRequest> requests(Long roomId);

  void markRemoved(Long roomId, Long userId, Instant at);

  boolean isRemoved(Long roomId, Long userId);

  Optional<RoomPerson> person(Long userId);

  List<RoomPerson> friendsWithoutARoom(Long userId, int limit);

  void dismissSuggestions(Long userId, Collection<Long> friendIds, Instant at);

  int addInvitations(Long roomId, Long inviterId, Collection<Long> userIds, Instant at);

  long countInvitees(Long roomId);

  List<RoomInvitee> invitees(Long roomId);

  Optional<Long> inviterOf(Long roomId, Long userId);

  boolean dropInvitation(Long roomId, Long userId);

  int dropInvitationsBy(Long roomId, Long inviterId);

  boolean declineInvitation(Long roomId, Long userId, Instant at);

  List<RoomInvitation> invitationsFor(Long userId, int limit);
}
