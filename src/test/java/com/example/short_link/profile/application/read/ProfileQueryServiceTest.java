package com.example.short_link.profile.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.short_link.link.application.ShortLinkUrlBuilder;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.stats.domain.repository.ClickEventReadRepository;
import com.example.short_link.profile.application.MyProfile;
import com.example.short_link.profile.application.PublicProfile;
import com.example.short_link.profile.domain.ProfileBlockEntity;
import com.example.short_link.profile.domain.ProfileBlockType;
import com.example.short_link.profile.domain.UsernameHistoryEntity;
import com.example.short_link.profile.domain.repository.ProfileBlockRepository;
import com.example.short_link.profile.domain.repository.UsernameHistoryRepository;
import com.example.short_link.profile.exception.ProfileException;
import com.example.short_link.support.TestEntities;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.exception.UserException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProfileQueryServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private LinkRepository linkRepository;
  @Mock private ClickEventReadRepository clickRepository;
  @Mock private UsernameHistoryRepository usernameHistoryRepository;
  @Mock private ProfileBlockRepository profileBlockRepository;
  @Mock private ShortLinkUrlBuilder urlBuilder;

  private ProfileQueryService service;

  @BeforeEach
  void setUp() {
    service =
        new ProfileQueryService(
            userRepository,
            linkRepository,
            clickRepository,
            usernameHistoryRepository,
            profileBlockRepository,
            urlBuilder,
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
  void findByUsernameNotFoundThrows() {
    when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
    when(usernameHistoryRepository.findFirstByOldUsernameAndExpiresAtAfter(
            any(), any(Instant.class)))
        .thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.findByUsername("ghost")).isInstanceOf(ProfileException.class);
  }

  @Test
  void findByUsernameSkipsDeletedAccount() {
    UserEntity u = userWithId(7L);
    u.softDelete();
    when(userRepository.findByUsername("alice")).thenReturn(Optional.of(u));
    when(usernameHistoryRepository.findFirstByOldUsernameAndExpiresAtAfter(
            any(), any(Instant.class)))
        .thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.findByUsername("alice")).isInstanceOf(ProfileException.class);
  }

  @Test
  void findByUsernameNullArgumentReturnsNotFound() {
    when(userRepository.findByUsername("")).thenReturn(Optional.empty());
    when(usernameHistoryRepository.findFirstByOldUsernameAndExpiresAtAfter(
            any(), any(Instant.class)))
        .thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.findByUsername(null)).isInstanceOf(ProfileException.class);
  }

  @Test
  void findByUsernameResolvesByHistory() {
    UserEntity u = userWithId(7L);
    u.claimUsername("now");
    when(userRepository.findByUsername("old")).thenReturn(Optional.empty());
    when(usernameHistoryRepository.findFirstByOldUsernameAndExpiresAtAfter(
            any(), any(Instant.class)))
        .thenReturn(
            Optional.of(new UsernameHistoryEntity(7L, "old", Instant.now().plusSeconds(60))));
    when(userRepository.findById(7L)).thenReturn(Optional.of(u));
    when(linkRepository.findAllByUserIdAndProfileOrderIsNotNullOrderByProfileOrderAsc(7L))
        .thenReturn(List.of());
    when(profileBlockRepository.findAllByUserIdOrderByProfileOrderAsc(7L)).thenReturn(List.of());
    PublicProfile p = service.findByUsername("old");
    assertThat(p.username()).isEqualTo("now");
  }

  @Test
  void findByUsernameRendersLinkAndBlockEntries() {
    UserEntity u = userWithId(7L);
    u.claimUsername("alice");
    LinkEntity link = new LinkEntity("https://example.com", "abc", 7L, null);
    TestEntities.withId(link, 1L);
    link.setProfileOrder(1);
    ProfileBlockEntity divider = new ProfileBlockEntity(7L, ProfileBlockType.DIVIDER, null, 2);
    TestEntities.withId(divider, 11L);
    ProfileBlockEntity textBlock = new ProfileBlockEntity(7L, ProfileBlockType.TEXT, "hello", 3);
    TestEntities.withId(textBlock, 12L);
    when(userRepository.findByUsername("alice")).thenReturn(Optional.of(u));
    when(linkRepository.findAllByUserIdAndProfileOrderIsNotNullOrderByProfileOrderAsc(7L))
        .thenReturn(List.of(link));
    when(profileBlockRepository.findAllByUserIdOrderByProfileOrderAsc(7L))
        .thenReturn(List.of(divider, textBlock));
    when(clickRepository.countsByLinkIds(any())).thenReturn(List.of());
    when(urlBuilder.build("abc")).thenReturn("https://kurl/abc");
    PublicProfile p = service.findByUsername("alice");
    assertThat(p.entries()).hasSize(3);
    assertThat(p.entries().get(0).kind()).isEqualTo("LINK");
    assertThat(p.entries().get(1).kind()).isEqualTo("DIVIDER");
    assertThat(p.entries().get(2).kind()).isEqualTo("TEXT");
  }
}
