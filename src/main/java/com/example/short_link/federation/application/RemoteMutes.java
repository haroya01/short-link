package com.example.short_link.federation.application;

import com.example.short_link.federation.domain.FederationFollowingEntity;
import com.example.short_link.federation.domain.OnRemoteAccount;
import com.example.short_link.federation.domain.RemoteAccountStanding;
import com.example.short_link.federation.domain.RemoteActorEntity;
import com.example.short_link.federation.domain.RemoteMuteEntity;
import com.example.short_link.federation.domain.repository.FederationFollowingRepository;
import com.example.short_link.federation.domain.repository.RemoteAccountStandingReader;
import com.example.short_link.federation.domain.repository.RemoteMuteRepository;
import com.example.short_link.federation.exception.FederationErrorCode;
import com.example.short_link.federation.exception.FederationException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RemoteMutes {

  private static final RemoteAccountStanding MUTED = RemoteAccountStanding.NONE.withMuting(true);

  private final RemoteMuteRepository mutes;
  private final RemoteAccountStandingReader standings;
  private final FederationFollowingRepository followings;
  private final RemoteFollowing following;
  private final Clock clock;

  @Transactional
  public RemoteAccountView mute(
      Long userId, Long remoteActorId, Boolean notifications, Long duration) {
    RemoteActorEntity remote = following.remote(remoteActorId);
    if (duration != null
        && (duration < RemoteMuteEntity.MIN_DURATION_SECONDS
            || duration > RemoteMuteEntity.MAX_DURATION_SECONDS)) {
      throw new FederationException(FederationErrorCode.MUTE_DURATION_INVALID);
    }
    boolean hide = notifications == null || notifications;
    Instant expiresAt =
        duration == null
            ? null
            : clock.instant().truncatedTo(ChronoUnit.MICROS).plusSeconds(duration);
    Optional<RemoteMuteEntity> existing = mutes.find(userId, remote.getId());
    if (existing.isPresent()) {
      existing.get().change(hide, expiresAt);
    } else {
      mutes.save(new RemoteMuteEntity(userId, remote.getId(), hide, expiresAt));
    }
    return following.view(userId, remote, following.standing(userId, remote).withMuting(true));
  }

  @Transactional
  public RemoteAccountView unmute(Long userId, Long remoteActorId) {
    RemoteActorEntity remote = following.known(remoteActorId);
    mutes.find(userId, remote.getId()).ifPresent(mutes::delete);
    return following.view(userId, remote, following.standing(userId, remote).withMuting(false));
  }

  @Transactional(readOnly = true)
  public List<RemoteMuteView> mutes(Long userId, int page, int size) {
    Instant now = clock.instant();
    List<OnRemoteAccount<RemoteMuteEntity>> rows = mutes.active(userId, now, page * size, size);
    List<Long> ids = rows.stream().map(row -> row.actor().getId()).toList();
    Map<Long, RemoteAccountStanding> marks = standings.of(userId, ids, now);
    Map<Long, FederationFollowingEntity> follows =
        followings.findAll(userId, ids).stream()
            .collect(
                Collectors.toMap(FederationFollowingEntity::getRemoteActorId, Function.identity()));
    return rows.stream()
        .map(
            row ->
                new RemoteMuteView(
                    RemoteAccountView.of(
                        row.actor(),
                        Optional.ofNullable(follows.get(row.actor().getId())),
                        marks.getOrDefault(row.actor().getId(), MUTED)),
                    row.row().getExpiresAt(),
                    row.row().isHideNotifications()))
        .toList();
  }
}
