package com.example.short_link.user.domain.repository;

import com.example.short_link.user.domain.*;
import java.util.Optional;

public interface UserTwoFactorRepository {

  Optional<UserTwoFactorEntity> findById(Long id);

  UserTwoFactorEntity save(UserTwoFactorEntity twoFactor);
}
