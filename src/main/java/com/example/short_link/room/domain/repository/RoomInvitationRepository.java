package com.example.short_link.room.domain.repository;

import com.example.short_link.room.domain.RoomInvitation;
import com.example.short_link.room.domain.RoomInvitee;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RoomInvitationRepository {

  int addInvitations(Long roomId, Long inviterId, Collection<Long> userIds, Instant at);

  long countInvitees(Long roomId);

  List<RoomInvitee> invitees(Long roomId);

  Optional<Long> inviterOf(Long roomId, Long userId);

  boolean dropInvitation(Long roomId, Long userId);

  int dropInvitationsBy(Long roomId, Long inviterId);

  boolean declineInvitation(Long roomId, Long userId, Instant at);

  List<RoomInvitation> invitationsFor(Long userId, int limit);
}
