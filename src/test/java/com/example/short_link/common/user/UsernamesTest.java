package com.example.short_link.common.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UsernamesTest {

  @Test
  void aUsernameIsReadTrimmedAndInLowerCase() {
    assertThat(Usernames.canonical("  Alice_01 ")).isEqualTo("alice_01");
    assertThat(Usernames.canonical(null)).isEmpty();
  }

  @Test
  void theGrammarTakesThreeToSixteenLettersDigitsOrUnderscoresNotStartingWithOne() {
    assertThat("abc".matches(Usernames.GRAMMAR)).isTrue();
    assertThat("a".repeat(16).matches(Usernames.GRAMMAR)).isTrue();
    assertThat("ab".matches(Usernames.GRAMMAR)).isFalse();
    assertThat("a".repeat(17).matches(Usernames.GRAMMAR)).isFalse();
    assertThat("_abc".matches(Usernames.GRAMMAR)).isFalse();
    assertThat("Abc".matches(Usernames.GRAMMAR)).isFalse();
  }
}
