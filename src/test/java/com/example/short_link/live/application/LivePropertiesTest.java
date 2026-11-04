package com.example.short_link.live.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class LivePropertiesTest {

  private final LiveProperties defaults =
      new LiveProperties(null, 0, null, null, null, null, null, null);

  @Test
  void unsetValuesFallBackToTheDocumentedDefaults() {
    assertThat(defaults.maxStreams()).isEqualTo(500);
    assertThat(defaults.maxStreamsPerUser()).isEqualTo(4);
    assertThat(defaults.lifetime()).isEqualTo(Duration.ofMinutes(5));
    assertThat(defaults.heartbeat()).isEqualTo(Duration.ofSeconds(25));
  }

  @Test
  void lifetimesSpreadBetweenFourAndSixMinutesAndTheEmitterOutlivesTheLongest() {
    assertThat(defaults.lifetimeAt(0.0)).isEqualTo(Duration.ofMinutes(4));
    assertThat(defaults.lifetimeAt(1.0)).isEqualTo(Duration.ofMinutes(6));
    assertThat(defaults.hardLimit()).isEqualTo(Duration.ofMinutes(6).plusSeconds(50));
    assertThat(defaults.tokenTtl()).isEqualTo(Duration.ofMinutes(10));
  }

  @Test
  void aZeroCapStaysZero() {
    assertThat(new LiveProperties(0, 4, null, null, null, null, null, null).maxStreams()).isZero();
  }

  @Test
  void theWindowGrowsWithEveryOpenStreamSoAllRefetchesStayNearFiftyASecond() {
    assertThat(defaults.windowFor(1)).isEqualTo(Duration.ofMillis(300));
    assertThat(defaults.windowFor(15)).isEqualTo(Duration.ofMillis(300));
    assertThat(defaults.windowFor(50)).isEqualTo(Duration.ofSeconds(1));
    assertThat(defaults.windowFor(500)).isEqualTo(Duration.ofSeconds(10));
    assertThat(defaults.windowFor(5000)).isEqualTo(Duration.ofSeconds(10));
  }
}
