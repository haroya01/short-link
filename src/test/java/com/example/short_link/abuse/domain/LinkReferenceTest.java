package com.example.short_link.abuse.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LinkReferenceTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "abc123",
        "  abc123  ",
        "kurl.me/abc123",
        "https://kurl.me/abc123",
        "https://kurl.me/abc123?src=sms#top",
        "https://kurl.me/abc123+",
        "http://go.example.com/abc123/",
      })
  void findsTheCodeInWhatReportersPaste(String input) {
    assertThat(LinkReference.shortCodeOf(input)).contains("abc123");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "kurl.me",
        "https://kurl.me/",
        "https://kurl.me/u/someone",
        "https://kurl.me/ab",
        "https://kurl.me/abc-123",
        "https://kurl.me/thisCodeIsFarTooLong17",
      })
  void rejectsWhatCannotBeAShortCode(String input) {
    assertThat(LinkReference.shortCodeOf(input)).isEmpty();
  }

  @Test
  void nullIsNotACode() {
    assertThat(LinkReference.shortCodeOf(null)).isEmpty();
  }
}
