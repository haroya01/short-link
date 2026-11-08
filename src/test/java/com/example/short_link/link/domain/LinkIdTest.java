package com.example.short_link.link.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class LinkIdTest {

  @Test
  void anIdStartsAtOne() {
    assertThat(new LinkId(1L).value()).isEqualTo(1L);
    assertThatThrownBy(() -> new LinkId(0L)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new LinkId(null)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void anIdReadsAsItsNumberWhereItKeysPerLinkState() {
    assertThat("velocity:" + new LinkId(42L)).isEqualTo("velocity:42");
  }
}
