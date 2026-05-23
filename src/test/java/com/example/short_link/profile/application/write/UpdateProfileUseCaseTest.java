package com.example.short_link.profile.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.profile.application.InvalidUsernameException;
import com.example.short_link.profile.application.MyProfile;
import com.example.short_link.profile.application.UsernameTakenException;
import com.example.short_link.profile.domain.UsernameHistoryEntity;
import com.example.short_link.profile.domain.UsernameHistoryRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UpdateProfileUseCaseTest {

  @Mock private UserRepository userRepository;
  @Mock private UsernameHistoryRepository usernameHistoryRepository;

  private SimpleMeterRegistry meterRegistry;
  private UpdateProfileUseCase useCase;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    useCase = new UpdateProfileUseCase(userRepository, usernameHistoryRepository, meterRegistry);
    ReflectionTestUtils.setField(useCase, "publicProfileBaseUrl", "https://kurl.app/u/");
  }

  private UserEntity userWithId(long id) {
    UserEntity u = new UserEntity("u@x.com", "google", "g-" + id);
    writeField(u, "id", id);
    return u;
  }

  @Test
  void changesBioAndTheme() {
    UserEntity u = userWithId(7L);
    when(userRepository.findById(7L)).thenReturn(Optional.of(u));
    MyProfile p = useCase.execute(new UpdateProfileCommand(7L, null, "new bio", "dark", null));
    assertThat(u.getBio()).isEqualTo("new bio");
    assertThat(u.getProfileTheme()).isEqualTo("dark");
    assertThat(p.bio()).isEqualTo("new bio");
    assertThat(meterRegistry.counter("profile.updated").count()).isEqualTo(1.0);
  }

  @Test
  void bioTooLongThrows() {
    UserEntity u = userWithId(7L);
    when(userRepository.findById(7L)).thenReturn(Optional.of(u));
    String big = "x".repeat(300);
    assertThatThrownBy(() -> useCase.execute(new UpdateProfileCommand(7L, null, big, null, null)))
        .isInstanceOf(InvalidUsernameException.class);
  }

  @Test
  void blankBioClears() {
    UserEntity u = userWithId(7L);
    u.updateBio("existing");
    when(userRepository.findById(7L)).thenReturn(Optional.of(u));
    useCase.execute(new UpdateProfileCommand(7L, null, "   ", null, null));
    assertThat(u.getBio()).isNull();
  }

  @Test
  void usernameValidatesFormat() {
    UserEntity u = userWithId(7L);
    when(userRepository.findById(7L)).thenReturn(Optional.of(u));
    assertThatThrownBy(() -> useCase.execute(new UpdateProfileCommand(7L, "AB", null, null, null)))
        .isInstanceOf(InvalidUsernameException.class);
    assertThatThrownBy(
            () -> useCase.execute(new UpdateProfileCommand(7L, "has space", null, null, null)))
        .isInstanceOf(InvalidUsernameException.class);
  }

  @Test
  void usernameRejectsReserved() {
    UserEntity u = userWithId(7L);
    when(userRepository.findById(7L)).thenReturn(Optional.of(u));
    assertThatThrownBy(
            () -> useCase.execute(new UpdateProfileCommand(7L, "admin", null, null, null)))
        .isInstanceOf(InvalidUsernameException.class);
  }

  @Test
  void usernameRejectsTakenByOtherUser() {
    UserEntity me = userWithId(7L);
    UserEntity other = userWithId(8L);
    when(userRepository.findById(7L)).thenReturn(Optional.of(me));
    when(userRepository.findByUsername("alice")).thenReturn(Optional.of(other));
    assertThatThrownBy(
            () -> useCase.execute(new UpdateProfileCommand(7L, "alice", null, null, null)))
        .isInstanceOf(UsernameTakenException.class);
  }

  @Test
  void usernameRejectsHistoryHoldByOther() {
    UserEntity me = userWithId(7L);
    UsernameHistoryEntity hist =
        new UsernameHistoryEntity(99L, "alice", Instant.now().plusSeconds(60));
    when(userRepository.findById(7L)).thenReturn(Optional.of(me));
    when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());
    when(usernameHistoryRepository.findFirstByOldUsernameAndExpiresAtAfter(
            any(), any(Instant.class)))
        .thenReturn(Optional.of(hist));
    assertThatThrownBy(
            () -> useCase.execute(new UpdateProfileCommand(7L, "alice", null, null, null)))
        .isInstanceOf(UsernameTakenException.class);
  }

  @Test
  void usernameStoresHistoryWhenRenaming() {
    UserEntity me = userWithId(7L);
    me.claimUsername("old");
    when(userRepository.findById(7L)).thenReturn(Optional.of(me));
    when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());
    when(usernameHistoryRepository.findFirstByOldUsernameAndExpiresAtAfter(
            any(), any(Instant.class)))
        .thenReturn(Optional.empty());
    useCase.execute(new UpdateProfileCommand(7L, "  ALICE  ", null, null, null));
    verify(usernameHistoryRepository).save(any(UsernameHistoryEntity.class));
    assertThat(me.getUsername()).isEqualTo("alice");
  }

  @Test
  void socialsNormalized() {
    UserEntity u = userWithId(7L);
    when(userRepository.findById(7L)).thenReturn(Optional.of(u));
    String socials = "[{\"channel\":\"x\",\"url\":\"https://x.com/me\"}]";
    useCase.execute(new UpdateProfileCommand(7L, null, null, null, socials));
    assertThat(u.getSocials()).contains("\"x\"");
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
