package com.example.short_link.live.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class LivePropertiesTest {

  private final LiveProperties defaults = new LiveProperties(null, 0, null, null, null, null, null);

  @Test
  void unsetValuesFallBackToTheDocumentedDefaults() {
    assertThat(defaults.maxStreams()).isEqualTo(500);
    assertThat(defaults.maxStreamsPerUser()).isEqualTo(4);
    assertThat(defaults.lifetime()).isEqualTo(Duration.ofMinutes(5));
    assertThat(defaults.heartbeat()).isEqualTo(Duration.ofSeconds(25));
  }

  @Test
  void aZeroCapStaysZero() {
    assertThat(new LiveProperties(0, 4, null, null, null, null, null).maxStreams()).isZero();
  }

  @Test
  void theWindowGrowsWithEveryOpenStreamSoAllRefetchesStayNearAHundredASecond() {
    assertThat(defaults.windowFor(1)).isEqualTo(Duration.ofMillis(300));
    assertThat(defaults.windowFor(30)).isEqualTo(Duration.ofMillis(300));
    assertThat(defaults.windowFor(100)).isEqualTo(Duration.ofSeconds(1));
    assertThat(defaults.windowFor(500)).isEqualTo(Duration.ofSeconds(5));
    assertThat(defaults.windowFor(5000)).isEqualTo(Duration.ofSeconds(5));
  }
}
