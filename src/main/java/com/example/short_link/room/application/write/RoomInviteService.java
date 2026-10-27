package com.example.short_link.room.application.write;

import com.example.short_link.link.application.write.CreateLinkCommand;
import com.example.short_link.link.application.write.CreateLinkUseCase;
import com.example.short_link.room.application.read.RoomInviteUrls;
import com.example.short_link.room.application.read.RoomInviteView;
import com.example.short_link.room.domain.RoomEntity;
import com.example.short_link.room.domain.RoomInviteEntity;
import com.example.short_link.room.domain.RoomInviteUnusable;
import com.example.short_link.room.domain.repository.RoomRepository;
import com.example.short_link.room.exception.RoomErrorCode;
import com.example.short_link.room.exception.RoomException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomInviteService {

  static final int MAX_USES = 50;
  static final Duration SHORTEST = Duration.ofMinutes(5);
  static final Duration LONGEST = Duration.ofDays(30);

  private static final String ALPHABET =
      "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
  private static final int CODE_LENGTH = 10;
  private static final SecureRandom RANDOM = new SecureRandom();

  public enum Joined {
    JOINED,
    REQUESTED,
    ALREADY_MEMBER
  }

  public record JoinResult(Joined status, Long roomId) {}

  private final RoomRepository rooms;
  private final RoomWriteGuard guard;
  private final RoomInviteUrls urls;
  private final CreateLinkUseCase links;
  private final Clock clock;

  // Any member shares one. The kurl link is a convenience: if issuing it fails the code still
  // works.
  @Transactional
  public RoomInviteView create(
      Long userId, Long roomId, Integer maxUses, Long expiresInSeconds, Boolean approval) {
    guard.requireMember(userId, roomId);
    if ((maxUses != null && (maxUses < 1 || maxUses > MAX_USES))
        || (expiresInSeconds != null
            && (expiresInSeconds < SHORTEST.toSeconds()
                || expiresInSeconds > LONGEST.toSeconds()))) {
      throw new RoomException(RoomErrorCode.ROOM_INVITE_INVALID, MAX_USES);
    }
    Instant now = now();
    RoomInviteEntity invite =
        rooms.save(
            new RoomInviteEntity(
                roomId,
                userId,
                code(),
                maxUses,
                Boolean.TRUE.equals(approval),
                expiresInSeconds == null ? null : now.plusSeconds(expiresInSeconds)));
    try {
      invite.linkAs(
          links
              .execute(
                  new CreateLinkCommand(
                      urls.page(invite.getCode()), userId, null, null, false, true))
              .shortCode()
              .value());
    } catch (RuntimeException e) {
      log.warn("room invite link skipped for room {}: {}", roomId, e.getMessage());
    }
    return RoomInviteView.of(invite, urls, rooms.person(userId).orElse(null));
  }

  // The owner stops any invite, a member their own.
  @Transactional
  public RoomInviteView disable(Long userId, Long roomId, Long inviteId) {
    RoomEntity room =
        rooms
            .findById(roomId)
            .filter(found -> rooms.isMember(roomId, userId))
            .orElseThrow(() -> new RoomException(RoomErrorCode.ROOM_NOT_FOUND, roomId));
    RoomInviteEntity invite =
        rooms
            .invite(roomId, inviteId)
            .filter(found -> room.isOwnedBy(userId) || found.getCreatorId().equals(userId))
            .orElseThrow(() -> new RoomException(RoomErrorCode.ROOM_INVITE_NOT_FOUND));
    invite.disable(now());
    return RoomInviteView.of(invite, urls, rooms.person(invite.getCreatorId()).orElse(null));
  }

  // The invite and then the room are locked, so two joins cannot both take the last use or seat.
  @Transactional
  public JoinResult join(Long userId, String code) {
    RoomInviteEntity invite =
        rooms
            .lockInvite(code)
            .orElseThrow(() -> new RoomException(RoomErrorCode.ROOM_INVITE_NOT_FOUND));
    Long roomId = invite.getRoomId();
    RoomEntity room =
        rooms
            .lock(roomId)
            .orElseThrow(() -> new RoomException(RoomErrorCode.ROOM_INVITE_NOT_FOUND));
    if (rooms.isMember(roomId, userId)) {
      return new JoinResult(Joined.ALREADY_MEMBER, roomId);
    }
    if (rooms.isRemoved(roomId, userId)) {
      throw new RoomException(RoomErrorCode.ROOM_REMOVED);
    }
    if (rooms.hasRequested(roomId, userId)) {
      return new JoinResult(Joined.REQUESTED, roomId);
    }
    Instant now = now();
    invite
        .unusableAt(now)
        .ifPresent(
            unusable -> {
              throw new RoomException(RoomErrorCode.ROOM_INVITE_UNUSABLE, unusable.apiName())
                  .with("reason", unusable.apiName());
            });
    if (rooms.countMembers(roomId) >= RoomEntity.MAX_MEMBERS) {
      throw new RoomException(RoomErrorCode.ROOM_FULL, RoomEntity.MAX_MEMBERS)
          .with("reason", RoomInviteUnusable.FULL.apiName());
    }
    invite.use();
    if (invite.isApproval() || room.isApprovalRequired()) {
      rooms.request(roomId, userId, invite.getId(), now);
      return new JoinResult(Joined.REQUESTED, roomId);
    }
    rooms.addMember(roomId, userId, now);
    return new JoinResult(Joined.JOINED, roomId);
  }

  @Transactional
  public void approve(Long ownerId, Long roomId, Long userId) {
    RoomEntity room = guard.owned(ownerId, roomId);
    if (!rooms.withdrawRequest(roomId, userId)) {
      throw new RoomException(RoomErrorCode.ROOM_REQUEST_NOT_FOUND, userId);
    }
    if (rooms.countMembers(roomId) >= RoomEntity.MAX_MEMBERS) {
      throw new RoomException(RoomErrorCode.ROOM_FULL, RoomEntity.MAX_MEMBERS);
    }
    rooms.addMember(room.getId(), userId, now());
  }

  @Transactional
  public void reject(Long ownerId, Long roomId, Long userId) {
    guard.owned(ownerId, roomId);
    if (!rooms.withdrawRequest(roomId, userId)) {
      throw new RoomException(RoomErrorCode.ROOM_REQUEST_NOT_FOUND, userId);
    }
  }

  private static String code() {
    StringBuilder code = new StringBuilder(CODE_LENGTH);
    for (int i = 0; i < CODE_LENGTH; i++) {
      code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
    }
    return code.toString();
  }

  private Instant now() {
    return clock.instant().truncatedTo(ChronoUnit.MICROS);
  }
}
