package com.example.short_link.profile.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.profile.application.MyProfile;
import com.example.short_link.profile.application.PublicProfile;
import com.example.short_link.profile.application.PublicProfileSnapshot;
import com.example.short_link.profile.application.PublicProfileSnapshot.LinkWindow;
import com.example.short_link.support.TestEntities;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.exception.UserException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProfileQueryServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-10T00:00:00Z");

  @Mock private UserRepository userRepository;
  @Mock private LinkRepository linkRepository;
  @Mock private PublicProfileLoader publicProfiles;
  @Mock private PublicHandleReader publicHandles;

  private ProfileQueryService service;

  @BeforeEach
  void setUp() {
    service =
        new ProfileQueryService(
            userRepository,
            linkRepository,
            publicProfiles,
            publicHandles,
            Clock.fixed(NOW, ZoneOffset.UTC),
            "https://kurl.app/u/");
  }

  private UserEntity userWithId(long id) {
    UserEntity u = new UserEntity("u@x.com", "google", "g-" + id);
    TestEntities.withId(u, id);
    return u;
  }

  @Test
  void myProfileThrowsWhenUserMissing() {
    when(userRepository.findById(7L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.myProfile(7L)).isInstanceOf(UserException.class);
  }

  @Test
  void myProfileReturnsCurrentState() {
    UserEntity u = userWithId(7L);
    u.claimUsername("alice");
    u.updateBio("hi");
    when(userRepository.findById(7L)).thenReturn(Optional.of(u));
    MyProfile p = service.myProfile(7L);
    assertThat(p.username()).isEqualTo("alice");
    assertThat(p.publicUrl()).isEqualTo("https://kurl.app/u/alice");
    assertThat(p.bio()).isEqualTo("hi");
  }

  @Test
  void myProfileWithoutUsernameHasNullPublicUrl() {
    UserEntity u = userWithId(7L);
    when(userRepository.findById(7L)).thenReturn(Optional.of(u));
    assertThat(service.myProfile(7L).publicUrl()).isNull();
  }

  @Test
  void aServedProfileHidesLinksOutsideTheirWindowAtThatMoment() {
    PublicProfile profile =
        new PublicProfile(
            "alice",
            null,
            null,
            null,
            null,
            List.of(),
            List.of(
                PublicProfile.ProfileEntry.link(
                    new ShortCode("open1"),
                    "https://kurl/open1",
                    "https://a",
                    null,
                    null,
                    0,
                    false),
                PublicProfile.ProfileEntry.link(
                    new ShortCode("later"),
                    "https://kurl/later",
                    "https://b",
                    null,
                    null,
                    0,
                    false),
                PublicProfile.ProfileEntry.link(
                    new ShortCode("gone1"),
                    "https://kurl/gone1",
                    "https://c",
                    null,
                    null,
                    0,
                    false),
                PublicProfile.ProfileEntry.text(9L, "hello")),
            0L,
            false);
    when(publicProfiles.load("alice"))
        .thenReturn(
            new PublicProfileSnapshot(
                profile,
                List.of(
                    new LinkWindow(
                        new ShortCode("open1"), NOW.minusSeconds(1), NOW.plusSeconds(60), false),
                    new LinkWindow(new ShortCode("later"), NOW.plusSeconds(1), null, false),
                    new LinkWindow(new ShortCode("gone1"), null, NOW, false))));

    PublicProfile served = service.findByUsername("alice");

    assertThat(served.entries())
        .extracting(PublicProfile.ProfileEntry::kind, e -> String.valueOf(e.shortCode()))
        .containsExactly(tuple("LINK", "open1"), tuple("TEXT", "null"));
    verifyNoInteractions(linkRepository);
  }

  @Test
  void aServedProfileDropsAViewLimitedLinkWhoseLastViewIsSpent() {
    PublicProfile profile =
        new PublicProfile(
            "alice",
            null,
            null,
            null,
            null,
            List.of(),
            List.of(
                PublicProfile.ProfileEntry.link(
                    new ShortCode("spent"),
                    "https://kurl/spent",
                    "https://a",
                    null,
                    null,
                    1,
                    false),
                PublicProfile.ProfileEntry.link(
                    new ShortCode("room1"),
                    "https://kurl/room1",
                    "https://b",
                    null,
                    null,
                    1,
                    false),
                PublicProfile.ProfileEntry.link(
                    new ShortCode("free1"),
                    "https://kurl/free1",
                    "https://c",
                    null,
                    null,
                    0,
                    false)),
            0L,
            false);
    when(publicProfiles.load("alice"))
        .thenReturn(
            new PublicProfileSnapshot(
                profile,
                List.of(
                    new LinkWindow(new ShortCode("spent"), null, null, true),
                    new LinkWindow(new ShortCode("room1"), null, null, true))));
    when(linkRepository.findShortCodesWithViewLimitReached(
            Set.of(new ShortCode("spent"), new ShortCode("room1"))))
        .thenReturn(List.of(new ShortCode("spent")));

    PublicProfile served = service.findByUsername("alice");

    assertThat(served.entries())
        .extracting(e -> e.shortCode().value())
        .containsExactly("room1", "free1");
  }
}
