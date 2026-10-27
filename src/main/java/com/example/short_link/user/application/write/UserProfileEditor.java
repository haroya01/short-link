package com.example.short_link.user.application.write;

import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserProfileEditor {

  private final UserRepository users;

  public void claimUsername(Long userId, String username) {
    require(userId).claimUsername(username);
  }

  public void updateBio(Long userId, String bio) {
    require(userId).updateBio(bio);
  }

  public void updateDisplayName(Long userId, String displayName) {
    require(userId).updateDisplayName(displayName);
  }

  public void updateProfileTheme(Long userId, String theme) {
    require(userId).updateProfileTheme(theme);
  }

  public void updateSocials(Long userId, String socials) {
    require(userId).updateSocials(socials);
  }

  public void updateHideFollowerCount(Long userId, boolean hide) {
    require(userId).updateHideFollowerCount(hide);
  }

  public void updateLocked(Long userId, boolean locked) {
    require(userId).updateLocked(locked);
  }

  public boolean updateStatsPublic(Long userId, boolean statsPublic) {
    UserEntity user = require(userId);
    user.updateStatsPublic(statsPublic);
    users.save(user);
    return user.isStatsPublic();
  }

  private UserEntity require(Long userId) {
    return users
        .findById(userId)
        .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
  }
}
