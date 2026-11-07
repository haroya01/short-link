package com.example.short_link.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class UnlistedKeysTest {

  @ParameterizedTest(name = "expected={0} given={1} opens={2}")
  @CsvSource(
      nullValues = "null",
      textBlock =
          """
          abcDEF123, abcDEF123,  true
          abcDEF123, abcDEF12,   false
          abcDEF123, abcDEF1234, false
          abcDEF123, ABCdef123,  false
          abcDEF123, null,       false
          null,      abcDEF123,  false
          null,      null,       false
          """)
  void onlyTheExactKeyOpensAndAMissingKeyNeverDoes(String expected, String given, boolean opens) {
    assertThat(UnlistedKeys.matches(expected, given)).isEqualTo(opens);
  }

  @Test
  void aNewKeyIsThirtyTwoUrlSafeCharactersAndNeverRepeats() {
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < 1000; i++) {
      String key = UnlistedKeys.next();
      assertThat(key).hasSize(32).matches("[A-Za-z0-9_-]+");
      seen.add(key);
    }
    assertThat(seen).hasSize(1000);
  }
}
