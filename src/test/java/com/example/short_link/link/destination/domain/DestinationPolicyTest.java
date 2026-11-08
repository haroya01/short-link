package com.example.short_link.link.destination.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DestinationPolicyTest {

  @Test
  void aLabelIsTrimmedCappedAtFortyAndAbsentWhenEmpty() {
    assertThat(DestinationPolicy.sanitizeLabel(null)).isNull();
    assertThat(DestinationPolicy.sanitizeLabel("   ")).isNull();
    assertThat(DestinationPolicy.sanitizeLabel("  Spring sale  ")).isEqualTo("Spring sale");
    assertThat(DestinationPolicy.sanitizeLabel("x".repeat(41))).isEqualTo("x".repeat(40));
  }
}
