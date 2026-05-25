package com.example.short_link.link.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.application.helper.VisitorHasher;
import org.junit.jupiter.api.Test;

class VisitorHasherTest {

  @Test
  void sameInputProducesSameHash() {
    String h1 = VisitorHasher.hash(1L, "1.2.3.4", "Mozilla/5.0");
    String h2 = VisitorHasher.hash(1L, "1.2.3.4", "Mozilla/5.0");
    assertThat(h1).isEqualTo(h2).hasSize(64);
  }

  @Test
  void differentLinkIdProducesDifferentHash() {
    String h1 = VisitorHasher.hash(1L, "1.2.3.4", "ua");
    String h2 = VisitorHasher.hash(2L, "1.2.3.4", "ua");
    assertThat(h1).isNotEqualTo(h2);
  }

  @Test
  void differentIpProducesDifferentHash() {
    assertThat(VisitorHasher.hash(1L, "1.2.3.4", "ua"))
        .isNotEqualTo(VisitorHasher.hash(1L, "5.6.7.8", "ua"));
  }

  @Test
  void handlesNullInputs() {
    String h = VisitorHasher.hash(null, null, null);
    assertThat(h).hasSize(64);
  }
}
