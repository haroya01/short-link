package com.example.short_link.user.application.write;

import com.example.short_link.user.application.read.MuteStatus;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserMuteEntity;
import com.example.short_link.user.domain.repository.MuteRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MuteUseCase {

  private final UserRepository userRepository;
  private final MuteRepository muteRepository;
  private final Clock clock;

  @Transactional
  public MuteStatus mute(Long userId, String username, Boolean notifications, Long duration) {
    UserEntity target = resolve(username);
    if (target.getId().equals(userId)) {
      throw new UserException(UserErrorCode.CANNOT_MUTE_SELF);
    }
    if (duration != null && (duration < 60 || duration > UserMuteEntity.MAX_DURATION_SECONDS)) {
      throw new UserException(UserErrorCode.MUTE_DURATION_INVALID);
    }
    boolean hide = notifications == null || notifications;
    Instant expiresAt =
        duration == null
            ? null
            : clock.instant().truncatedTo(ChronoUnit.MICROS).plusSeconds(duration);
    UserMuteEntity mute =
        muteRepository
            .find(userId, target.getId())
            .map(
                existing -> {
                  existing.change(hide, expiresAt);
                  return existing;
                })
            .orElseGet(
                () ->
                    muteRepository.save(
                        new UserMuteEntity(userId, target.getId(), hide, expiresAt)));
    return new MuteStatus(true, mute.isHideNotifications(), mute.getExpiresAt());
  }

  @Transactional
  public void unmute(Long userId, String username) {
    UserEntity target = resolve(username);
    muteRepository.find(userId, target.getId()).ifPresent(muteRepository::delete);
  }

  @Transactional(readOnly = true)
  public MuteStatus status(Long userId, String username) {
    UserEntity target = resolve(username);
    return muteRepository
        .find(userId, target.getId())
        .filter(mute -> mute.activeAt(clock.instant()))
        .map(mute -> new MuteStatus(true, mute.isHideNotifications(), mute.getExpiresAt()))
        .orElse(MuteStatus.NONE);
  }

  private UserEntity resolve(String username) {
    return userRepository
        .findByUsername(username)
        .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
  }
}
