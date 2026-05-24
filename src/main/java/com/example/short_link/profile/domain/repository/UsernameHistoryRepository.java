package com.example.short_link.profile.domain.repository;

import com.example.short_link.profile.domain.*;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsernameHistoryRepository extends JpaRepository<UsernameHistoryEntity, Long> {

  Optional<UsernameHistoryEntity> findFirstByOldUsernameAndExpiresAtAfter(
      String oldUsername, Instant now);
}
