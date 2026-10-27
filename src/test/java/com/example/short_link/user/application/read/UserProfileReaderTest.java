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

class UserProfileReaderTest {

  private final UserRepository users = mock(UserRepository.class);
  private final UserProfileReader reader = new UserProfileReader(users);

  private static UserEntity user(long id, String username) {
    UserEntity user = new UserEntity(username + "@x.com", "google", "g-" + id);
    TestEntities.withId(user, id);
    user.claimUsername(username);
    return user;
  }

  @Test
  void carriesTheProfileFieldsOfTheAccount() {
    UserEntity alice = user(1L, "alice");
    alice.updateDisplayName("Alice");
    alice.updateBio("writes");
    alice.updateAvatar("https://cdn/a.png", "avatars/1");
    alice.updateBanner("https://cdn/b.png", "banners/1");
    alice.updateProfileTheme("dark");
    alice.updateSocials("[{\"kind\":\"github\",\"url\":\"https://github.com/alice\"}]");
    alice.updateHideFollowerCount(true);
    alice.updateLocked(true);
    alice.updateStatsPublic(true);
    alice.changeTimezone("America/New_York");
    when(users.findById(1L)).thenReturn(Optional.of(alice));

    assertThat(reader.find(1L))
        .contains(
            new UserProfile(
                1L,
                "alice",
                "Alice",
                "writes",
                "https://cdn/a.png",
                "https://cdn/b.png",
                "dark",
                "[{\"kind\":\"github\",\"url\":\"https://github.com/alice\"}]",
                true,
                true,
                true,
                "America/New_York"));
  }

  @Test
  void activeLookupsSkipDeletedAccountsThatPlainLookupsStillFind() {
    UserEntity gone = user(2L, "gone");
    gone.softDelete();
    when(users.findById(2L)).thenReturn(Optional.of(gone));
    when(users.findByUsername("gone")).thenReturn(Optional.of(gone));

    assertThat(reader.find(2L)).map(UserProfile::username).contains("gone");
    assertThat(reader.findByUsername("gone")).map(UserProfile::id).contains(2L);
    assertThat(reader.findActive(2L)).isEmpty();
    assertThat(reader.findActiveByUsername("gone")).isEmpty();
  }

  @Test
  void publicHandlesAreCountedAndPagedByTheRepository() {
    when(users.countByUsernameIsNotNullAndDeletedAtIsNull()).thenReturn(3L);
    when(users.findPublicHandles(1, 2)).thenReturn(List.of("carol"));

    assertThat(reader.countPublicHandles()).isEqualTo(3L);
    assertThat(reader.publicHandles(1, 2)).containsExactly("carol");
  }
}
