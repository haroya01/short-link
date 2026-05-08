package com.example.short_link.user.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

  Optional<UserEntity> findByOauthProviderAndOauthId(String oauthProvider, String oauthId);

  Optional<UserEntity> findByEmail(String email);

  long countByCreatedAtAfter(Instant since);

  List<UserEntity> findTop200ByDeletedAtBefore(Instant cutoff);
}
