package com.example.short_link.user.application.twofactor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class RecoveryCodesTest {

  private final PasswordEncoder encoder = mock(PasswordEncoder.class);
  private final RecoveryCodes codes = new RecoveryCodes(encoder);

  @Test
  void issuesTenReadableCodesWithTheExistingAlphabetAndGrouping() {
    assertThat(codes.generate())
        .hasSize(10)
        .doesNotHaveDuplicates()
        .allSatisfy(
            code ->
                assertThat(code)
                    .matches(
                        "[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{5}-[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{5}"));
    verifyNoInteractions(encoder);
  }

  @Test
  void delegatesHashingAndNormalizedMatchingToTheConfiguredEncoder() {
    when(encoder.encode("ABCDE-23456")).thenReturn("stored-hash");
    when(encoder.matches("ABCDE-23456", "stored-hash")).thenReturn(true);

    assertThat(codes.hashAll(List.of("ABCDE-23456"))).containsExactly("stored-hash");
    assertThat(codes.matchingHash(List.of("stored-hash"), "  abcde-23456  "))
        .contains("stored-hash");
    assertThat(codes.matchingHash(List.of("stored-hash"), "wrong")).isEmpty();
  }

  @Test
  void missingCodeDoesNotInvokeTheEncoder() {
    assertThat(codes.matchingHash(List.of("stored-hash"), null)).isEmpty();
    assertThat(codes.matchingHash(List.of("stored-hash"), " ")).isEmpty();
    verifyNoInteractions(encoder);
  }
}
