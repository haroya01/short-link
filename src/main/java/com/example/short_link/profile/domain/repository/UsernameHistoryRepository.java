package com.example.short_link.profile.domain.repository;

import com.example.short_link.profile.domain.*;
import java.time.Instant;
import java.util.Optional;

public interface UsernameHistoryRepository {

  UsernameHistoryEntity save(UsernameHistoryEntity history);

  Optional<UsernameHistoryEntity> findFirstByOldUsernameAndExpiresAtAfter(
      String oldUsername, Instant now);
}
