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
  void infrastructureSubdomainsAreReservedBecauseHandlesBecomeSubdomains() {
    assertThat(ReservedUsernames.ALL).contains("blog", "origin", "links", "mail", "www", "api");
  }

  @Test
  void roleAccountsAreReservedBecauseHandlesBecomeFederatedAddresses() {
    assertThat(ReservedUsernames.ALL)
        .contains("abuse", "security", "postmaster", "webmaster", "noreply", "official", "staff");
  }

  @Test
  void everyReservedNameIsOneAUserCouldOtherwiseClaim() {
    assertThat(ReservedUsernames.ALL)
        .filteredOn(name -> name.length() >= 3)
        .allMatch(name -> name.matches("^[a-z0-9][a-z0-9_]{2,15}$"));
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
