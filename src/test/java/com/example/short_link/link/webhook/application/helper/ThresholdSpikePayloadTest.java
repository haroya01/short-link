package com.example.short_link.link.webhook.application.helper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ThresholdSpikePayloadTest {

  private ThresholdSpikePayload sample() {
    return new ThresholdSpikePayload("abc12345", "10m", 87L, 50, "t.co");
  }

  @Test
  void typeIsSpikeAlert() {
    assertThat(sample().toJsonMap()).containsEntry("type", "spike_alert");
  }

  @Test
  void shortCodeIsPropagated() {
    assertThat(sample().toJsonMap()).containsEntry("shortCode", "abc12345");
  }

  @Test
  void windowIsHumanString() {
    assertThat(sample().toJsonMap()).containsEntry("window", "10m");
  }

  @Test
  void clicksIsPropagated() {
    assertThat(sample().toJsonMap()).containsEntry("clicks", 87L);
  }

  @Test
  void thresholdIsPropagated() {
    assertThat(sample().toJsonMap()).containsEntry("threshold", 50);
  }

  @Test
  void topReferrerCanBeNull() {
    var p = new ThresholdSpikePayload("x", "10m", 60L, 50, null);
    assertThat(p.toJsonMap()).containsEntry("topReferrer", null);
  }
}
