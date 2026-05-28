package com.example.short_link.user.infrastructure.persistence;

import com.example.short_link.user.domain.UserEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaUserRepository extends JpaRepository<UserEntity, Long> {

  Optional<UserEntity> findByOauthProviderAndOauthId(String oauthProvider, String oauthId);

  Optional<UserEntity> findByEmail(String email);

  Optional<UserEntity> findByStripeCustomerId(String stripeCustomerId);

  Optional<UserEntity> findByUsername(String username);

  long countByCreatedAtAfter(Instant since);

  long countByUsernameIsNotNullAndDeletedAtIsNull();

  List<UserEntity> findAllByUsernameIsNotNullAndDeletedAtIsNullOrderByCreatedAtAsc(
      Pageable pageable);

  List<UserEntity> findTop200ByDeletedAtBefore(Instant cutoff);
}
