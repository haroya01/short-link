package com.example.short_link.link.access.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.short_link.common.security.UserAccessLookup;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.exception.LinkException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LinkAccessGuardTest {

  @Mock private UserAccessLookup users;
  private LinkAccessGuard guard;

  @BeforeEach
  void setUp() {
    guard = new LinkAccessGuard(users);
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
    when(users.isAdmin(99L)).thenReturn(true);
    assertThat(guard.canView(99L, link)).isTrue();
  }

  @Test
  void nonOwnerNonAdminCannotView() {
    LinkEntity link = new LinkEntity("https://x", "abc", 7L, null);
    when(users.isAdmin(99L)).thenReturn(false);
    assertThat(guard.canView(99L, link)).isFalse();
  }

  @Test
  void unknownUserCannotView() {
    LinkEntity link = new LinkEntity("https://x", "abc", 7L, null);
    when(users.isAdmin(99L)).thenReturn(false);
    assertThat(guard.canView(99L, link)).isFalse();
  }

  @Test
  void requireViewThrowsWhenDenied() {
    LinkEntity link = new LinkEntity("https://x", "abc", 7L, null);
    when(users.isAdmin(99L)).thenReturn(false);
    assertThatThrownBy(() -> guard.requireView(99L, link)).isInstanceOf(LinkException.class);
  }

  @Test
  void requireViewSucceedsForOwner() {
    LinkEntity link = new LinkEntity("https://x", "abc", 7L, null);
    guard.requireView(7L, link);
  }
}
