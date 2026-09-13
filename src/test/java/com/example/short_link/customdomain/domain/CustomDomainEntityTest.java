package com.example.short_link.customdomain.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.test.util.ReflectionTestUtils;

class CustomDomainEntityTest {

  @Test
  void constructorEnforcesReservedDomainAndFormatRules() {
    for (String invalid :
        new String[] {null, " ", "kurl.me", "GO.KURL.ME", "bad_host.example.com"}) {
      assertThatThrownBy(() -> new CustomDomainEntity(1L, invalid, "token"))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  @ResourceLock("java.util.Locale.default")
  void domainIdentityDoesNotDependOnTheServerLocale() {
    Locale original = Locale.getDefault();
    try {
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));
      CustomDomainEntity domain = new CustomDomainEntity(1L, " LINKS.EXAMPLE.COM ", "token");

      assertThat(domain.getDomain()).isEqualTo("links.example.com");
    } finally {
      Locale.setDefault(original);
    }
  }

  @Test
  void successfulReverificationRetainsTheOriginalVerificationTime() {
    CustomDomainEntity domain = new CustomDomainEntity(1L, "go.example.com", "token");
    domain.markVerified();
    Instant firstVerifiedAt = Instant.parse("2026-01-01T00:00:00Z");
    ReflectionTestUtils.setField(domain, "verifiedAt", firstVerifiedAt);
    ReflectionTestUtils.setField(domain, "lastCheckedAt", firstVerifiedAt);

    domain.markVerified();

    assertThat(domain.isVerified()).isTrue();
    assertThat(domain.getVerifiedAt()).isEqualTo(firstVerifiedAt);
    assertThat(domain.getLastCheckedAt()).isAfter(firstVerifiedAt);
  }
}
