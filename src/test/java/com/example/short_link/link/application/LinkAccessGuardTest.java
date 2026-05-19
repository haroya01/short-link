package com.example.short_link.link.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LinkAccessGuardTest {

  @Mock private UserRepository userRepository;
  private LinkAccessGuard guard;

  @BeforeEach
  void setUp() {
    guard = new LinkAccessGuard(userRepository);
  }

  @Test
  void anonymousCannotView() {
    LinkEntity link = new LinkEntity("https://x", "abc", 1L, null);
    assertThat(guard.canView(null, link)).isFalse();
  }

  @Test
  void ownerCanView() {
    LinkEntity link = new LinkEntity("https://x", "abc", 7L, null);
    assertThat(guard.canView(7L, link)).isTrue();
  }

  @Test
  void adminCanViewAnyLink() {
    LinkEntity link = new LinkEntity("https://x", "abc", 7L, null);
    UserEntity admin = new UserEntity("a@x", "google", "g-1");
    admin.promoteToAdmin();
    when(userRepository.findById(99L)).thenReturn(Optional.of(admin));
    assertThat(guard.canView(99L, link)).isTrue();
  }

  @Test
  void nonOwnerNonAdminCannotView() {
    LinkEntity link = new LinkEntity("https://x", "abc", 7L, null);
    UserEntity user = new UserEntity("u@x", "google", "g-1");
    when(userRepository.findById(99L)).thenReturn(Optional.of(user));
    assertThat(guard.canView(99L, link)).isFalse();
  }

  @Test
  void unknownUserCannotView() {
    LinkEntity link = new LinkEntity("https://x", "abc", 7L, null);
    when(userRepository.findById(99L)).thenReturn(Optional.empty());
    assertThat(guard.canView(99L, link)).isFalse();
  }

  @Test
  void requireViewThrowsWhenDenied() {
    LinkEntity link = new LinkEntity("https://x", "abc", 7L, null);
    when(userRepository.findById(99L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> guard.requireView(99L, link))
        .isInstanceOf(LinkNotOwnedException.class);
  }

  @Test
  void requireViewSucceedsForOwner() {
    LinkEntity link = new LinkEntity("https://x", "abc", 7L, null);
    guard.requireView(7L, link);
  }
}
