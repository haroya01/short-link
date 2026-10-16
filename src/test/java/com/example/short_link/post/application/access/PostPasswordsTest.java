package com.example.short_link.post.application.access;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PostPasswordsTest {

  private final PostPasswords passwords = new PostPasswords();

  @Test
  void aLongKoreanPasswordIsCheckedToItsLastCharacter() {
    String longest = "가".repeat(199) + "나";
    String hash = passwords.hash(longest);

    assertThat(hash).startsWith("$2a$10$").hasSize(60);
    assertThat(passwords.matches(longest, hash)).isTrue();
    assertThat(passwords.matches("가".repeat(199) + "다", hash)).isFalse();
  }

  @Test
  void nothingMatchesAMissingPasswordOrHash() {
    assertThat(passwords.matches(null, passwords.hash("four"))).isFalse();
    assertThat(passwords.matches("four", null)).isFalse();
  }

  @Test
  void lengthCountsCharactersNotBytesAndNothingIsTrimmed() {
    assertThat(PostPasswords.acceptableLength("abc")).isFalse();
    assertThat(PostPasswords.acceptableLength(" ab ")).isTrue();
    assertThat(PostPasswords.acceptableLength("😀😀😀😀")).isTrue();
    assertThat(PostPasswords.acceptableLength("😀".repeat(200))).isTrue();
    assertThat(PostPasswords.acceptableLength("😀".repeat(201))).isFalse();
  }
}
