package com.example.short_link.profile.application.write;

import com.example.short_link.common.event.AccountUnlockedEvent;
import com.example.short_link.profile.application.MyProfile;
import com.example.short_link.profile.application.MyProfileMapper;
import com.example.short_link.profile.application.ProfileCacheEviction;
import com.example.short_link.profile.application.ReservedUsernames;
import com.example.short_link.profile.application.Socials;
import com.example.short_link.profile.domain.UsernameHistoryEntity;
import com.example.short_link.profile.domain.repository.UsernameHistoryRepository;
import com.example.short_link.profile.exception.ProfileErrorCode;
import com.example.short_link.profile.exception.ProfileException;
import com.example.short_link.user.application.read.UserProfile;
import com.example.short_link.user.application.read.UserProfileReader;
import com.example.short_link.user.application.write.UserProfileEditor;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateProfileUseCase {

  private static final Pattern USERNAME = Pattern.compile("^[a-z0-9][a-z0-9_]{2,15}$");

  private static final Duration USERNAME_GRACE = Duration.ofDays(30);

  private final UserProfileReader users;
  private final UserProfileEditor editor;
  private final UsernameHistoryRepository usernameHistoryRepository;
  private final MeterRegistry meterRegistry;
  private final ProfileCacheEviction cacheEviction;
  private final ApplicationEventPublisher events;
  private final Clock clock;
  private final String publicProfileBaseUrl;

  public UpdateProfileUseCase(
      UserProfileReader users,
      UserProfileEditor editor,
      UsernameHistoryRepository usernameHistoryRepository,
      MeterRegistry meterRegistry,
      ProfileCacheEviction cacheEviction,
      ApplicationEventPublisher events,
      Clock clock,
      @Value("${short-link.public-profile-base-url:http://localhost:3001/u/}")
          String publicProfileBaseUrl) {
    this.users = users;
    this.editor = editor;
    this.usernameHistoryRepository = usernameHistoryRepository;
    this.meterRegistry = meterRegistry;
    this.cacheEviction = cacheEviction;
    this.events = events;
    this.clock = clock;
    this.publicProfileBaseUrl = publicProfileBaseUrl;
  }

  @Transactional
  public MyProfile execute(UpdateProfileCommand cmd) {
    UserProfile user =
        users.find(cmd.userId()).orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
    String previousUsername = user.username();
    updateUsername(user, cmd.username(), cmd.userId());
    updateAppearance(cmd);
    updateLocked(user, cmd.locked());
    UserProfile updated = users.find(cmd.userId()).orElseThrow();
    cacheEviction.evictByUsername(previousUsername);
    String currentUsername = updated.username();
    if (currentUsername != null && !currentUsername.equals(previousUsername)) {
      cacheEviction.evictByUsername(currentUsername);
    }
    meterRegistry.counter("profile.updated").increment();
    return MyProfileMapper.from(updated, publicProfileBaseUrl);
  }

  private void updateUsername(UserProfile user, String requestedUsername, Long userId) {
    if (requestedUsername == null) {
      return;
    }
    String normalized = requestedUsername.trim().toLowerCase(Locale.ROOT);
    validateUsername(normalized);
    if (normalized.equals(user.username())) {
      return;
    }
    if (ReservedUsernames.ALL.contains(normalized)) {
      throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "reserved");
    }
    users
        .findByUsername(normalized)
        .filter(other -> !other.id().equals(userId))
        .ifPresent(
            other -> {
              throw new ProfileException(ProfileErrorCode.USERNAME_TAKEN, normalized);
            });
    usernameHistoryRepository
        .findFirstByOldUsernameAndExpiresAtAfter(normalized, clock.instant())
        .filter(history -> !history.getUserId().equals(userId))
        .ifPresent(
            history -> {
              throw new ProfileException(ProfileErrorCode.USERNAME_TAKEN, normalized);
            });
    String previous = user.username();
    if (previous != null && !previous.isBlank()) {
      usernameHistoryRepository.save(
          new UsernameHistoryEntity(userId, previous, clock.instant().plus(USERNAME_GRACE)));
    }
    editor.claimUsername(userId, normalized);
  }

  private void updateAppearance(UpdateProfileCommand cmd) {
    Long userId = cmd.userId();
    if (cmd.bio() != null) {
      String trimmed = cmd.bio().trim();
      if (trimmed.length() > 280) {
        throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "bio too long");
      }
      editor.updateBio(userId, trimmed.isEmpty() ? null : trimmed);
    }
    if (cmd.displayName() != null) {
      String name = cmd.displayName().strip().replaceAll("\\s+", " ");
      if (name.codePointCount(0, name.length()) > 30) {
        throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "display name too long");
      }
      editor.updateDisplayName(userId, name.isEmpty() ? null : name);
    }
    if (cmd.theme() != null) {
      editor.updateProfileTheme(userId, cmd.theme());
    }
    if (cmd.socials() != null) {
      editor.updateSocials(userId, Socials.normalize(cmd.socials()));
    }
    if (cmd.hideFollowerCount() != null) {
      editor.updateHideFollowerCount(userId, cmd.hideFollowerCount());
    }
  }

  private void updateLocked(UserProfile user, Boolean locked) {
    if (locked == null || locked == user.locked()) {
      return;
    }
    editor.updateLocked(user.id(), locked);
    if (!locked) {
      events.publishEvent(new AccountUnlockedEvent(user.id()));
    }
  }

  private static void validateUsername(String username) {
    if (username.isBlank()) throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "blank");
    if (!USERNAME.matcher(username).matches()) {
      throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "invalid format");
    }
  }
}
