package com.example.short_link.link.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CountryBlocklistTest {

  @Test
  void normalizeReturnsUppercaseUniqueCsv() {
    assertThat(CountryBlocklist.normalize("kr, jp , us , kr")).isEqualTo("KR,JP,US");
  }

  @Test
  void normalizeIgnoresInvalidCodesAndBlanks() {
    assertThat(CountryBlocklist.normalize("kor, j, fr, , ")).isEqualTo("FR");
    assertThat(CountryBlocklist.normalize("   ")).isNull();
    assertThat(CountryBlocklist.normalize(null)).isNull();
  }

  @Test
  void containsIsCaseInsensitive() {
    CountryBlocklist blocklist = CountryBlocklist.fromCsv("KR,JP");
    assertThat(blocklist.contains("kr")).isTrue();
    assertThat(blocklist.contains("US")).isFalse();
    assertThat(blocklist.contains(null)).isFalse();
  }
}
