package com.example.short_link.user.infrastructure.persistence;

import com.example.short_link.user.domain.UserTwoFactorEntity;
import com.example.short_link.user.domain.repository.UserTwoFactorRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaUserTwoFactorRepository
    extends JpaRepository<UserTwoFactorEntity, Long>, UserTwoFactorRepository {

  @Override
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT factor FROM UserTwoFactorEntity factor WHERE factor.userId = :userId")
  Optional<UserTwoFactorEntity> findByIdForUpdate(@Param("userId") Long userId);
}
