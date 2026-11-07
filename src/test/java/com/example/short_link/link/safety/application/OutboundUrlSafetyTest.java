package com.example.short_link.link.safety.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.security.BlockedDomainChecker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OutboundUrlSafetyTest {

  private final BlockedDomainChecker blocked = mock(BlockedDomainChecker.class);
  private final UrlSafetyChecker safety = mock(UrlSafetyChecker.class);
  private final OutboundUrlSafety outbound = new OutboundUrlSafety(blocked, safety);

  @ParameterizedTest(name = "web={0} blocked={1} safe={2} -> {3}")
  @CsvSource(
      textBlock =
          """
          true,  false, true,  true
          true,  true,  true,  false
          true,  false, false, false
          """)
  void aWebAddressOpensOnlyWhenNeitherBlockedNorUnsafe(
      boolean web, boolean isBlocked, boolean isSafe, boolean opens) {
    when(blocked.isBlocked("https://example.com/")).thenReturn(isBlocked);
    when(safety.isSafe("https://example.com/")).thenReturn(isSafe);

    assertThat(outbound.allows("https://example.com/")).isEqualTo(opens);
  }

  @Test
  void somethingThatIsNotAWebAddressIsRefusedWithoutAskingAnyone() {
    assertThat(outbound.allows("javascript:alert(1)")).isFalse();
    verify(blocked, never()).isBlocked(anyString());
    verify(safety, never()).isSafe(anyString());
  }
}
