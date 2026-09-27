package com.example.short_link.user.infrastructure.persistence;

import com.example.short_link.user.domain.UserEntity;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaUserRepository extends JpaRepository<UserEntity, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT u FROM UserEntity u WHERE u.id = :id")
  Optional<UserEntity> findByIdForUpdate(@Param("id") Long id);

  Optional<UserEntity> findByOauthProviderAndOauthId(String oauthProvider, String oauthId);

  Optional<UserEntity> findByEmail(String email);

  Optional<UserEntity> findByUsername(String username);

  long countByCreatedAtAfter(Instant since);

  long countByUsernameIsNotNullAndDeletedAtIsNull();

  List<UserEntity> findAllByUsernameIsNotNullAndDeletedAtIsNullOrderByCreatedAtAsc(
      Pageable pageable);

  List<UserEntity> findTop200ByDeletedAtBefore(Instant cutoff);
}
