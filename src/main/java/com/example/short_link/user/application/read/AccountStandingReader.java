package com.example.short_link.user.application.read;

import com.example.short_link.user.application.read.AccountStanding.Status;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AccountStandingReader {

  private final UserRepository users;
  private final Clock clock;

  @Transactional(readOnly = true)
  public Optional<AccountStanding> find(Long userId) {
    return users.findById(userId).map(user -> standing(user, clock.instant()));
  }

  private static AccountStanding standing(UserEntity user, Instant now) {
    if (user.isDeleted()) {
      return new AccountStanding(user.getCreatedAt(), Status.DELETED, null);
    }
    if (user.isBanned()) {
      return new AccountStanding(user.getCreatedAt(), Status.BANNED, null);
    }
    if (user.isSuspendedAt(now)) {
      return new AccountStanding(user.getCreatedAt(), Status.SUSPENDED, user.getSuspendedUntil());
    }
    return new AccountStanding(user.getCreatedAt(), Status.ACTIVE, null);
  }
}
