package com.example.short_link.profile.application.write;

import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateStatsVisibilityUseCase {

  private final UserRepository userRepository;

  @Transactional
  public boolean execute(Long ownerUserId, boolean statsPublic) {
    UserEntity owner =
        userRepository
            .findById(ownerUserId)
            .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
    owner.updateStatsPublic(statsPublic);
    userRepository.save(owner);
    return owner.isStatsPublic();
  }
}
