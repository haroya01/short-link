package com.example.short_link.user.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.short_link.user.application.read.AccountStanding.Status;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AccountStandingReaderTest {

  private static final Instant NOW = Instant.parse("2026-11-10T03:00:00Z");

  private final UserRepository users = mock(UserRepository.class);
  private final AccountStandingReader reader =
      new AccountStandingReader(users, Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void anAccountInGoodStandingIsActive() {
    account();

    assertThat(reader.find(5L)).get().extracting(AccountStanding::status).isEqualTo(Status.ACTIVE);
  }

  @Test
  void aSuspensionCountsOnlyUntilItEnds() {
    account().suspend(NOW.plusSeconds(3600));
    assertThat(reader.find(5L))
        .contains(new AccountStanding(null, Status.SUSPENDED, NOW.plusSeconds(3600)));

    account().suspend(NOW.minusSeconds(1));
    assertThat(reader.find(5L)).contains(new AccountStanding(null, Status.ACTIVE, null));
  }

  @Test
  void aBanAndADeletionAreTheirOwnStates() {
    account().ban();
    assertThat(reader.find(5L)).contains(new AccountStanding(null, Status.BANNED, null));

    UserEntity deleted = account();
    deleted.ban();
    deleted.softDelete();
    assertThat(reader.find(5L)).contains(new AccountStanding(null, Status.DELETED, null));
  }

  @Test
  void aMissingAccountHasNoStanding() {
    when(users.findById(5L)).thenReturn(Optional.empty());

    assertThat(reader.find(5L)).isEmpty();
  }

  private UserEntity account() {
    UserEntity user = new UserEntity("standing@example.com", "google", "g-standing");
    when(users.findById(5L)).thenReturn(Optional.of(user));
    return user;
  }
}
