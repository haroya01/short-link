package com.example.short_link.profile.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.link.application.LinkNotFoundException;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
import com.example.short_link.profile.application.ProfileService.ReorderItem;
import com.example.short_link.profile.domain.ProfileBlockEntity;
import com.example.short_link.profile.domain.ProfileBlockRepository;
import com.example.short_link.profile.domain.ProfileBlockType;
import com.example.short_link.profile.domain.UsernameHistoryEntity;
import com.example.short_link.profile.domain.UsernameHistoryRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private LinkRepository linkRepository;
  @Mock private UsernameHistoryRepository usernameHistoryRepository;
  @Mock private ProfileBlockRepository profileBlockRepository;

  private SimpleMeterRegistry meterRegistry;
  private ProfileService service;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    service =
        new ProfileService(
            userRepository,
            linkRepository,
            usernameHistoryRepository,
            profileBlockRepository,
            meterRegistry);
    ReflectionTestUtils.setField(service, "publicProfileBaseUrl", "https://kurl.app/u/");
  }

  private UserEntity userWithId(long id) {
    UserEntity u = new UserEntity("u@x.com", "google", "g-" + id);
    writeField(u, "id", id);
    return u;
  }

  @Test
  void updateProfileChangesBioAndTheme() {
    UserEntity u = userWithId(7L);
    when(userRepository.findById(7L)).thenReturn(Optional.of(u));
    MyProfile p = service.updateProfile(7L, null, "new bio", "dark", null);
    assertThat(u.getBio()).isEqualTo("new bio");
    assertThat(u.getProfileTheme()).isEqualTo("dark");
    assertThat(p.bio()).isEqualTo("new bio");
    assertThat(meterRegistry.counter("profile.updated").count()).isEqualTo(1.0);
  }

  @Test
  void updateProfileBioTooLongThrows() {
    UserEntity u = userWithId(7L);
    when(userRepository.findById(7L)).thenReturn(Optional.of(u));
    String big = "x".repeat(300);
    assertThatThrownBy(() -> service.updateProfile(7L, null, big, null, null))
        .isInstanceOf(InvalidUsernameException.class);
  }

  @Test
  void updateProfileBlankBioClears() {
    UserEntity u = userWithId(7L);
    u.updateBio("existing");
    when(userRepository.findById(7L)).thenReturn(Optional.of(u));
    service.updateProfile(7L, null, "   ", null, null);
    assertThat(u.getBio()).isNull();
  }

  @Test
  void updateProfileUsernameValidatesFormat() {
    UserEntity u = userWithId(7L);
    when(userRepository.findById(7L)).thenReturn(Optional.of(u));
    assertThatThrownBy(() -> service.updateProfile(7L, "AB", null, null, null))
        .isInstanceOf(InvalidUsernameException.class);
    assertThatThrownBy(() -> service.updateProfile(7L, "  ", null, null, null))
        .isInstanceOf(InvalidUsernameException.class);
    assertThatThrownBy(() -> service.updateProfile(7L, "has space", null, null, null))
        .isInstanceOf(InvalidUsernameException.class);
  }

  @Test
  void updateProfileUsernameRejectsReserved() {
    UserEntity u = userWithId(7L);
    when(userRepository.findById(7L)).thenReturn(Optional.of(u));
    assertThatThrownBy(() -> service.updateProfile(7L, "admin", null, null, null))
        .isInstanceOf(InvalidUsernameException.class);
  }

  @Test
  void updateProfileUsernameRejectsTakenByOtherUser() {
    UserEntity me = userWithId(7L);
    UserEntity other = userWithId(8L);
    when(userRepository.findById(7L)).thenReturn(Optional.of(me));
    when(userRepository.findByUsername("alice")).thenReturn(Optional.of(other));
    assertThatThrownBy(() -> service.updateProfile(7L, "alice", null, null, null))
        .isInstanceOf(UsernameTakenException.class);
  }

  @Test
  void updateProfileUsernameOkWhenSameUser() {
    UserEntity me = userWithId(7L);
    when(userRepository.findById(7L)).thenReturn(Optional.of(me));
    when(userRepository.findByUsername("alice")).thenReturn(Optional.of(me));
    when(usernameHistoryRepository.findFirstByOldUsernameAndExpiresAtAfter(
            any(), any(Instant.class)))
        .thenReturn(Optional.empty());
    service.updateProfile(7L, "alice", null, null, null);
    assertThat(me.getUsername()).isEqualTo("alice");
  }

  @Test
  void updateProfileUsernameRejectsHistoryHoldByOther() {
    UserEntity me = userWithId(7L);
    UsernameHistoryEntity hist =
        new UsernameHistoryEntity(99L, "alice", Instant.now().plusSeconds(60));
    when(userRepository.findById(7L)).thenReturn(Optional.of(me));
    when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());
    when(usernameHistoryRepository.findFirstByOldUsernameAndExpiresAtAfter(
            any(), any(Instant.class)))
        .thenReturn(Optional.of(hist));
    assertThatThrownBy(() -> service.updateProfile(7L, "alice", null, null, null))
        .isInstanceOf(UsernameTakenException.class);
  }

  @Test
  void updateProfileUsernameStoresHistoryWhenRenaming() {
    UserEntity me = userWithId(7L);
    me.claimUsername("old");
    when(userRepository.findById(7L)).thenReturn(Optional.of(me));
    when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());
    when(usernameHistoryRepository.findFirstByOldUsernameAndExpiresAtAfter(
            any(), any(Instant.class)))
        .thenReturn(Optional.empty());
    service.updateProfile(7L, "  ALICE  ", null, null, null);
    verify(usernameHistoryRepository).save(any(UsernameHistoryEntity.class));
    assertThat(me.getUsername()).isEqualTo("alice");
  }

  @Test
  void updateProfileSocialsDelegatesToNormalizer() {
    UserEntity u = userWithId(7L);
    when(userRepository.findById(7L)).thenReturn(Optional.of(u));
    String socials = "[{\"channel\":\"x\",\"url\":\"https://x.com/me\"}]";
    service.updateProfile(7L, null, null, null, socials);
    assertThat(u.getSocials()).contains("\"x\"");
  }

  @Test
  void createBlockSavesNewBlockWithNextOrder() {
    when(linkRepository.findAllByUserIdAndProfileOrderIsNotNullOrderByProfileOrderAsc(7L))
        .thenReturn(List.of());
    when(profileBlockRepository.countByUserId(7L)).thenReturn(2L);
    when(profileBlockRepository.save(any(ProfileBlockEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    ProfileBlockEntity created = service.createBlock(7L, ProfileBlockType.DIVIDER, null);
    assertThat(created.getType()).isEqualTo(ProfileBlockType.DIVIDER);
    assertThat(created.getProfileOrder()).isEqualTo(3);
  }

  @Test
  void createBlockRejectsImageWithoutUrl() {
    assertThatThrownBy(() -> service.createBlock(7L, ProfileBlockType.IMAGE, "  "))
        .isInstanceOf(InvalidUsernameException.class);
  }

  @Test
  void createBlockRejectsImageNonHttp() {
    assertThatThrownBy(() -> service.createBlock(7L, ProfileBlockType.IMAGE, "javascript:alert(1)"))
        .isInstanceOf(InvalidUsernameException.class);
  }

  @Test
  void createBlockRejectsImageMalformed() {
    assertThatThrownBy(() -> service.createBlock(7L, ProfileBlockType.IMAGE, "http://[bad"))
        .isInstanceOf(InvalidUsernameException.class);
  }

  @Test
  void createBlockRejectsImageTooLong() {
    String long2049 = "https://x.com/" + "a".repeat(2049);
    assertThatThrownBy(() -> service.createBlock(7L, ProfileBlockType.IMAGE, long2049))
        .isInstanceOf(InvalidUsernameException.class);
  }

  @Test
  void createBlockRejectsEmbedNonWhitelisted() {
    assertThatThrownBy(
            () ->
                service.createBlock(7L, ProfileBlockType.EMBED, "https://unknown-domain.example/x"))
        .isInstanceOf(InvalidUsernameException.class);
  }

  @Test
  void createBlockRejectsEmbedEmpty() {
    assertThatThrownBy(() -> service.createBlock(7L, ProfileBlockType.EMBED, ""))
        .isInstanceOf(InvalidUsernameException.class);
  }

  @Test
  void updateBlockNotFoundThrows() {
    when(profileBlockRepository.findById(11L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.updateBlock(7L, 11L, "x"))
        .isInstanceOf(ProfileNotFoundException.class);
  }

  @Test
  void updateBlockNotOwnedThrows() {
    ProfileBlockEntity block = new ProfileBlockEntity(99L, ProfileBlockType.TEXT, "x", 1);
    when(profileBlockRepository.findById(11L)).thenReturn(Optional.of(block));
    assertThatThrownBy(() -> service.updateBlock(7L, 11L, "x"))
        .isInstanceOf(ProfileNotFoundException.class);
  }

  @Test
  void updateBlockDividerHasNoContent() {
    ProfileBlockEntity block = new ProfileBlockEntity(7L, ProfileBlockType.DIVIDER, null, 1);
    when(profileBlockRepository.findById(11L)).thenReturn(Optional.of(block));
    assertThatThrownBy(() -> service.updateBlock(7L, 11L, "anything"))
        .isInstanceOf(InvalidUsernameException.class);
  }

  @Test
  void deleteBlockOwnerSucceeds() {
    ProfileBlockEntity block = new ProfileBlockEntity(7L, ProfileBlockType.TEXT, "x", 1);
    when(profileBlockRepository.findById(11L)).thenReturn(Optional.of(block));
    service.deleteBlock(7L, 11L);
    verify(profileBlockRepository).delete(block);
  }

  @Test
  void deleteBlockNotFoundThrows() {
    when(profileBlockRepository.findById(11L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.deleteBlock(7L, 11L))
        .isInstanceOf(ProfileNotFoundException.class);
  }

  @Test
  void deleteBlockNotOwnedThrows() {
    ProfileBlockEntity block = new ProfileBlockEntity(99L, ProfileBlockType.TEXT, "x", 1);
    when(profileBlockRepository.findById(11L)).thenReturn(Optional.of(block));
    assertThatThrownBy(() -> service.deleteBlock(7L, 11L))
        .isInstanceOf(ProfileNotFoundException.class);
  }

  @Test
  void reorderProfileAssignsOrdersInGivenSequence() {
    LinkEntity l1 = new LinkEntity("https://a", "a1", 7L, null);
    LinkEntity l2 = new LinkEntity("https://b", "b2", 7L, null);
    ProfileBlockEntity b1 = new ProfileBlockEntity(7L, ProfileBlockType.TEXT, "t", 99);
    writeField(b1, "id", 11L);
    when(linkRepository.findAllByUserIdAndProfileOrderIsNotNullOrderByProfileOrderAsc(7L))
        .thenReturn(List.of(l1, l2));
    when(profileBlockRepository.findAllByUserIdOrderByProfileOrderAsc(7L)).thenReturn(List.of(b1));

    java.util.List<ReorderItem> items = new java.util.ArrayList<>();
    items.add(new ReorderItem("LINK", "a1"));
    items.add(new ReorderItem("BLOCK", "11"));
    items.add(new ReorderItem("LINK", "b2"));
    items.add(new ReorderItem("UNKNOWN", "x"));
    items.add(null);
    items.add(new ReorderItem(null, "x"));
    items.add(new ReorderItem("LINK", null));
    items.add(new ReorderItem("LINK", "not-owned"));
    items.add(new ReorderItem("BLOCK", "not-a-number"));
    service.reorderProfile(7L, items);
    assertThat(l1.getProfileOrder()).isEqualTo(1);
    assertThat(b1.getProfileOrder()).isEqualTo(2);
    assertThat(l2.getProfileOrder()).isEqualTo(3);
  }

  @Test
  void setLinkHighlightThrowsWhenLinkMissing() {
    when(linkRepository.findByShortCode("missing")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.setLinkHighlight(7L, "missing", true))
        .isInstanceOf(LinkNotFoundException.class);
  }

  @Test
  void setLinkHighlightThrowsWhenNotOwner() {
    LinkEntity link = new LinkEntity("https://x", "abc", 99L, null);
    when(linkRepository.findByShortCode("abc")).thenReturn(Optional.of(link));
    assertThatThrownBy(() -> service.setLinkHighlight(7L, "abc", true))
        .isInstanceOf(LinkNotFoundException.class);
  }

  @Test
  void setLinkHighlightEnablesAndClearsOtherHighlights() {
    LinkEntity target = new LinkEntity("https://t", "abc", 7L, null);
    writeField(target, "id", 1L);
    LinkEntity existing = new LinkEntity("https://e", "xyz", 7L, null);
    writeField(existing, "id", 2L);
    existing.setProfileHighlighted(true);
    when(linkRepository.findByShortCode("abc")).thenReturn(Optional.of(target));
    when(linkRepository.findAllByUserIdAndProfileHighlightedIsTrue(7L))
        .thenReturn(List.of(existing));
    service.setLinkHighlight(7L, "abc", true);
    assertThat(target.isProfileHighlighted()).isTrue();
    assertThat(existing.isProfileHighlighted()).isFalse();
  }

  @Test
  void setLinkHighlightDisable() {
    LinkEntity target = new LinkEntity("https://t", "abc", 7L, null);
    target.setProfileHighlighted(true);
    when(linkRepository.findByShortCode("abc")).thenReturn(Optional.of(target));
    service.setLinkHighlight(7L, "abc", false);
    assertThat(target.isProfileHighlighted()).isFalse();
  }

  @Test
  void toggleLinkOnProfileShowAssignsNextOrder() {
    LinkEntity link = new LinkEntity("https://t", "abc", 7L, null);
    when(linkRepository.findByShortCode("abc")).thenReturn(Optional.of(link));
    when(linkRepository.findAllByUserIdAndProfileOrderIsNotNullOrderByProfileOrderAsc(7L))
        .thenReturn(List.of(link));
    when(profileBlockRepository.countByUserId(7L)).thenReturn(0L);
    service.toggleLinkOnProfile(7L, "abc", true);
    assertThat(link.getProfileOrder()).isEqualTo(2);
  }

  @Test
  void toggleLinkOnProfileHideClearsOrder() {
    LinkEntity link = new LinkEntity("https://t", "abc", 7L, null);
    link.setProfileOrder(5);
    when(linkRepository.findByShortCode("abc")).thenReturn(Optional.of(link));
    service.toggleLinkOnProfile(7L, "abc", false);
    assertThat(link.getProfileOrder()).isNull();
  }

  @Test
  void toggleLinkOnProfileMissingThrows() {
    when(linkRepository.findByShortCode("missing")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.toggleLinkOnProfile(7L, "missing", true))
        .isInstanceOf(LinkNotFoundException.class);
  }

  @Test
  void toggleLinkOnProfileNotOwnerThrows() {
    LinkEntity link = new LinkEntity("https://t", "abc", 99L, null);
    when(linkRepository.findByShortCode("abc")).thenReturn(Optional.of(link));
    assertThatThrownBy(() -> service.toggleLinkOnProfile(7L, "abc", true))
        .isInstanceOf(LinkNotFoundException.class);
  }

  private static void writeField(Object target, String name, Object value) {
    try {
      Field f = findField(target.getClass(), name);
      f.setAccessible(true);
      f.set(target, value);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  private static Field findField(Class<?> cls, String name) throws NoSuchFieldException {
    Class<?> c = cls;
    while (c != null) {
      try {
        return c.getDeclaredField(name);
      } catch (NoSuchFieldException ignored) {
        c = c.getSuperclass();
      }
    }
    throw new NoSuchFieldException(name);
  }
}
