package com.example.short_link.user.domain.repository;

import com.example.short_link.user.domain.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserRepository {

  Optional<UserEntity> findById(Long id);

  UserEntity save(UserEntity user);

  long count();

  boolean existsById(Long id);

  void deleteById(Long id);

  Optional<UserEntity> findByOauthProviderAndOauthId(String oauthProvider, String oauthId);

  Optional<UserEntity> findByEmail(String email);

  Optional<UserEntity> findByStripeCustomerId(String stripeCustomerId);

  Optional<UserEntity> findByUsername(String username);

  long countByCreatedAtAfter(Instant since);

  long countByUsernameIsNotNullAndDeletedAtIsNull();

  List<UserEntity> findTop200ByDeletedAtBefore(Instant cutoff);
}
