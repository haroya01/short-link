package com.example.short_link.room.domain.repository;

import com.example.short_link.room.domain.RoomInviteEntity;
import com.example.short_link.room.domain.RoomJoinRequest;
import com.example.short_link.room.domain.RoomPerson;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RoomInviteRepository {

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

  Optional<RoomPerson> person(Long userId);
}
