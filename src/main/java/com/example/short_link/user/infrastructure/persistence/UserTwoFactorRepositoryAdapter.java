package com.example.short_link.user.infrastructure.persistence;

import com.example.short_link.user.domain.UserTwoFactorEntity;
import com.example.short_link.user.domain.repository.UserTwoFactorRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class UserTwoFactorRepositoryAdapter implements UserTwoFactorRepository {

  private final JpaUserTwoFactorRepository jpa;

  @Override
  public Optional<UserTwoFactorEntity> findById(Long id) {
    return jpa.findById(id);
  }

  @Override
  public UserTwoFactorEntity save(UserTwoFactorEntity twoFactor) {
    return jpa.save(twoFactor);
  }
}
