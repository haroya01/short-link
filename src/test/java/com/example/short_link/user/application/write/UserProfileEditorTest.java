package com.example.short_link.user.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.support.TestEntities;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class UserProfileEditorTest {

  private final UserRepository users = mock(UserRepository.class);
  private final UserProfileEditor editor = new UserProfileEditor(users);

  private UserEntity stored(long id) {
    UserEntity user = new UserEntity("u@x.com", "google", "g-" + id);
    TestEntities.withId(user, id);
    when(users.findById(id)).thenReturn(Optional.of(user));
    return user;
  }

  @Test
  void eachEditLandsOnTheAccount() {
    UserEntity user = stored(7L);

    editor.claimUsername(7L, "alice");
    editor.updateBio(7L, "writes");
    editor.updateDisplayName(7L, "Alice");
    editor.updateProfileTheme(7L, "dark");
    editor.updateSocials(7L, "[]");
    editor.updateHideFollowerCount(7L, true);
    editor.updateLocked(7L, true);

    assertThat(user.getUsername()).isEqualTo("alice");
    assertThat(user.getBio()).isEqualTo("writes");
    assertThat(user.getDisplayName()).isEqualTo("Alice");
    assertThat(user.getProfileTheme()).isEqualTo("dark");
    assertThat(user.getSocials()).isEqualTo("[]");
    assertThat(user.isHideFollowerCount()).isTrue();
    assertThat(user.isLocked()).isTrue();
    verify(users, never()).save(user);
  }

  @Test
  void statsVisibilityIsSavedAndReadBackFromTheAccount() {
    UserEntity user = stored(7L);

    assertThat(editor.updateStatsPublic(7L, true)).isTrue();
    assertThat(user.isStatsPublic()).isTrue();
    verify(users).save(user);
  }

  @Test
  void anEditOfAMissingAccountIsUserNotFound() {
    when(users.findById(9L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> editor.updateBio(9L, "x"))
        .isInstanceOfSatisfying(
            UserException.class,
            e -> assertThat(e.errorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND));
  }
}
