package com.example.short_link.profile.application.write;

import com.example.short_link.profile.application.MyProfile;
import com.example.short_link.profile.application.MyProfileMapper;
import com.example.short_link.profile.application.ProfileCacheEviction;
import com.example.short_link.profile.application.ReservedUsernames;
import com.example.short_link.profile.application.Socials;
import com.example.short_link.profile.domain.UsernameHistoryEntity;
import com.example.short_link.profile.domain.repository.UsernameHistoryRepository;
import com.example.short_link.profile.exception.ProfileErrorCode;
import com.example.short_link.profile.exception.ProfileException;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateProfileUseCase {

  private static final Pattern USERNAME = Pattern.compile("^[a-z0-9][a-z0-9_]{2,15}$");

  /** Squat-protect old usernames for this long after a rename. */
  private static final Duration USERNAME_GRACE = Duration.ofDays(30);

  private final UserRepository userRepository;
  private final UsernameHistoryRepository usernameHistoryRepository;
  private final MeterRegistry meterRegistry;
  private final ProfileCacheEviction cacheEviction;

  @Value("${short-link.public-profile-base-url:http://localhost:3001/u/}")
  private String publicProfileBaseUrl;

  @Transactional
  public MyProfile execute(UpdateProfileCommand cmd) {
    UserEntity user =
        userRepository
            .findById(cmd.userId())
            .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
    if (cmd.username() != null) {
      String normalized = cmd.username().trim().toLowerCase();
      validateUsername(normalized);
      if (!normalized.equals(user.getUsername())) {
        if (ReservedUsernames.ALL.contains(normalized)) {
          throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "reserved");
        }
        userRepository
            .findByUsername(normalized)
            .filter(other -> !other.getId().equals(cmd.userId()))
            .ifPresent(
                other -> {
                  throw new ProfileException(ProfileErrorCode.USERNAME_TAKEN, normalized);
                });
        usernameHistoryRepository
            .findFirstByOldUsernameAndExpiresAtAfter(normalized, Instant.now())
            .filter(history -> !history.getUserId().equals(cmd.userId()))
            .ifPresent(
                history -> {
                  throw new ProfileException(ProfileErrorCode.USERNAME_TAKEN, normalized);
                });
        String previous = user.getUsername();
        if (previous != null && !previous.isBlank()) {
          usernameHistoryRepository.save(
              new UsernameHistoryEntity(
                  cmd.userId(), previous, Instant.now().plus(USERNAME_GRACE)));
        }
        user.claimUsername(normalized);
      }
    }
    if (cmd.bio() != null) {
      String trimmed = cmd.bio().trim();
      if (trimmed.length() > 280) {
        throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "bio too long");
      }
      user.updateBio(trimmed.isEmpty() ? null : trimmed);
    }
    if (cmd.theme() != null) {
      user.updateProfileTheme(cmd.theme());
    }
    if (cmd.socials() != null) {
      user.updateSocials(Socials.normalize(cmd.socials()));
    }
    meterRegistry.counter("profile.updated").increment();
    return MyProfileMapper.from(user, publicProfileBaseUrl);
  }

  private static void validateUsername(String username) {
    if (username.isBlank()) throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "blank");
    if (!USERNAME.matcher(username).matches()) {
      throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "invalid format");
    }
  }
}
