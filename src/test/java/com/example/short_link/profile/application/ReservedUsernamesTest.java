package com.example.short_link.profile.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReservedUsernamesTest {

  @Test
  void wellKnownRouteWordsAreReserved() {
    assertThat(ReservedUsernames.ALL)
        .contains("admin", "api", "auth", "login", "settings", "u", "users", "me", "billing");
  }

  @Test
  void normalHandlesAreNotReserved() {
    assertThat(ReservedUsernames.ALL).doesNotContain("alice", "bob", "haroya", "kurl_user");
  }

  @Test
  void containsAtLeastABaselineSet() {
    assertThat(ReservedUsernames.ALL).hasSizeGreaterThan(20);
  }
}
