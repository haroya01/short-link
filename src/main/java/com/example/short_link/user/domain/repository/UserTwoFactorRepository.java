package com.example.short_link.user.domain.repository;

import com.example.short_link.user.domain.*;
import java.util.Optional;

public interface UserTwoFactorRepository {

  Optional<UserTwoFactorEntity> findById(Long id);

  /** Serializes enrollment changes and one-time credential consumption until transaction commit. */
  Optional<UserTwoFactorEntity> findByIdForUpdate(Long id);

  UserTwoFactorEntity save(UserTwoFactorEntity twoFactor);
}
