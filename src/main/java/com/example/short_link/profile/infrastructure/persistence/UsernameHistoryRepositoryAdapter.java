package com.example.short_link.profile.infrastructure.persistence;

import com.example.short_link.profile.domain.UsernameHistoryEntity;
import com.example.short_link.profile.domain.repository.UsernameHistoryRepository;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class UsernameHistoryRepositoryAdapter implements UsernameHistoryRepository {

  private final JpaUsernameHistoryRepository jpa;

  @Override
  public UsernameHistoryEntity save(UsernameHistoryEntity history) {
    return jpa.save(history);
  }

  @Override
  public Optional<UsernameHistoryEntity> findFirstByOldUsernameAndExpiresAtAfter(
      String oldUsername, Instant now) {
    return jpa.findFirstByOldUsernameAndExpiresAtAfter(oldUsername, now);
  }
}
