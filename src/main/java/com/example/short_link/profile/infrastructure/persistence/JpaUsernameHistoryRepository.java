package com.example.short_link.profile.infrastructure.persistence;

import com.example.short_link.profile.domain.UsernameHistoryEntity;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaUsernameHistoryRepository extends JpaRepository<UsernameHistoryEntity, Long> {

  Optional<UsernameHistoryEntity> findFirstByOldUsernameAndExpiresAtAfter(
      String oldUsername, Instant now);
}
