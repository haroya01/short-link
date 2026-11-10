package com.example.short_link.link.access.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class LinkPasswordEncoderTest {

  private final LinkPasswordEncoder encoder = new LinkPasswordEncoder(4);
  private final BCryptPasswordEncoder undigested = new BCryptPasswordEncoder(4);

  @Test
  void aNewHashIsNotTheBcryptOfTheRawPassword() {
    String hash = encoder.encode("open-sesame");

    assertThat(hash).startsWith("$2a$04$").hasSize(60);
    assertThat(encoder.matches("open-sesame", hash)).isTrue();
    assertThat(undigested.matches("open-sesame", hash)).isFalse();
  }

  @Test
  void aHashStoredFromTheRawPasswordStillMatchesIt() {
    String stored = undigested.encode("open-sesame");

    assertThat(encoder.matches("open-sesame", stored)).isTrue();
    assertThat(encoder.matches("open-sesame!", stored)).isFalse();
  }

  @Test
  void aPasswordLongerThanBcryptReadsIsNeverComparedRaw() {
    String stored = undigested.encode("a".repeat(72));

    assertThat(encoder.matches("a".repeat(72), stored)).isTrue();
    assertThat(encoder.matches("a".repeat(72) + "b", stored)).isFalse();
  }

  @Test
  void aLongKoreanPasswordIsCheckedToItsLastCharacter() {
    String hash = encoder.encode("가".repeat(199) + "나");

    assertThat(encoder.matches("가".repeat(199) + "나", hash)).isTrue();
    assertThat(encoder.matches("가".repeat(199) + "다", hash)).isFalse();
  }

  @Test
  void nothingMatchesAMissingPasswordOrHash() {
    assertThat(encoder.matches(null, encoder.encode("four"))).isFalse();
    assertThat(encoder.matches("four", null)).isFalse();
  }
}
