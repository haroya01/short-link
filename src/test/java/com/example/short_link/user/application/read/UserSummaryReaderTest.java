package com.example.short_link.user.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.short_link.support.TestEntities;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class UserSummaryReaderTest {

  private final UserRepository users = mock(UserRepository.class);
  private final UserSummaryReader reader = new UserSummaryReader(users);

  private static UserEntity user(long id, String username) {
    UserEntity user = new UserEntity(username + "@x.com", "google", "g-" + id);
    TestEntities.withId(user, id);
    user.claimUsername(username);
    return user;
  }

  @Test
  void carriesTheCardFieldsOfTheAccount() {
    UserEntity alice = user(1L, "alice");
    alice.updateDisplayName("Alice");
    alice.updateBio("writes");
    alice.updateAvatar("https://cdn/a.png", "avatars/1");
    alice.updateLocale("ja");
    when(users.findById(1L)).thenReturn(Optional.of(alice));

    assertThat(reader.find(1L))
        .contains(new UserSummary(1L, "alice", "Alice", "https://cdn/a.png", "writes", "ja"));
  }

  @Test
  void findsDeletedAccountsByIdAndInBulk() {
    UserEntity gone = user(2L, "gone");
    gone.softDelete();
    when(users.findById(2L)).thenReturn(Optional.of(gone));
    when(users.findAllByIdIn(List.of(1L, 2L))).thenReturn(List.of(user(1L, "alice"), gone));

    assertThat(reader.find(2L)).map(UserSummary::username).contains("gone");
    assertThat(reader.findAll(List.of(1L, 2L)))
        .extracting(UserSummary::username)
        .containsExactly("alice", "gone");
  }

  @Test
  void activeLookupsByIdSkipDeletedAccounts() {
    UserEntity gone = user(2L, "gone");
    gone.softDelete();
    when(users.findById(1L)).thenReturn(Optional.of(user(1L, "alice")));
    when(users.findById(2L)).thenReturn(Optional.of(gone));
    when(users.findAllByIdIn(List.of(1L, 2L))).thenReturn(List.of(user(1L, "alice"), gone));

    assertThat(reader.findActive(1L)).map(UserSummary::username).contains("alice");
    assertThat(reader.findActive(2L)).isEmpty();
    assertThat(reader.findAllActive(List.of(1L, 2L)))
        .extracting(UserSummary::username)
        .containsExactly("alice");
  }

  @Test
  void activeHandlesComeFromTheRepositorysOwnActiveQuery() {
    when(users.findActiveByUsernameIn(List.of("alice", "gone")))
        .thenReturn(List.of(user(1L, "alice")));

    assertThat(reader.findActiveByUsernames(List.of("alice", "gone")))
        .extracting(UserSummary::username)
        .containsExactly("alice");
  }

  @Test
  void anActiveLookupByUsernameSkipsADeletedAccount() {
    UserEntity gone = user(2L, "gone");
    gone.softDelete();
    when(users.findByUsername("alice")).thenReturn(Optional.of(user(1L, "alice")));
    when(users.findByUsername("gone")).thenReturn(Optional.of(gone));

    assertThat(reader.findActiveByUsername("alice")).map(UserSummary::id).contains(1L);
    assertThat(reader.findActiveByUsername("gone")).isEmpty();
    assertThat(reader.findByUsername("gone")).map(UserSummary::id).contains(2L);
    assertThat(reader.findActiveByUsername("nobody")).isEmpty();
  }
}
