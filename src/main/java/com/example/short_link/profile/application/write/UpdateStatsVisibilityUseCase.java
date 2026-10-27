package com.example.short_link.profile.application.write;

import com.example.short_link.user.application.write.UserProfileEditor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateStatsVisibilityUseCase {

  private final UserProfileEditor profiles;

  @Transactional
  public boolean execute(Long ownerUserId, boolean statsPublic) {
    return profiles.updateStatsPublic(ownerUserId, statsPublic);
  }
}
