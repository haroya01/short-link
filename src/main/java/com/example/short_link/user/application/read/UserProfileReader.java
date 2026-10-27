package com.example.short_link.user.application.read;

import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserProfileReader {

  private final UserRepository users;

  public Optional<UserProfile> find(Long id) {
    return users.findById(id).map(UserProfileReader::profile);
  }

  public Optional<UserProfile> findActive(Long id) {
    return users.findById(id).filter(UserProfileReader::active).map(UserProfileReader::profile);
  }

  public Optional<UserProfile> findByUsername(String username) {
    return users.findByUsername(username).map(UserProfileReader::profile);
  }

  public Optional<UserProfile> findActiveByUsername(String username) {
    return users
        .findByUsername(username)
        .filter(UserProfileReader::active)
        .map(UserProfileReader::profile);
  }

  public long countPublicHandles() {
    return users.countByUsernameIsNotNullAndDeletedAtIsNull();
  }

  public List<String> publicHandles(int page, int size) {
    return users.findPublicHandles(page, size);
  }

  private static boolean active(UserEntity user) {
    return !user.isDeleted();
  }

  private static UserProfile profile(UserEntity user) {
    return new UserProfile(
        user.getId(),
        user.getUsername(),
        user.getDisplayName(),
        user.getBio(),
        user.getAvatarUrl(),
        user.getBannerUrl(),
        user.getProfileTheme(),
        user.getSocials(),
        user.isHideFollowerCount(),
        user.isLocked(),
        user.isStatsPublic(),
        user.getTimezone());
  }
}
