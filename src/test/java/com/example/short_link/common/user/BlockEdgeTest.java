package com.example.short_link.common.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BlockEdgeTest {

  @Test
  void theNamedStepsPutEachIdInItsRole() {
    BlockEdge edge = BlockEdge.blocker(1L).blocked(2L);

    assertThat(edge.blocker()).isEqualTo(1L);
    assertThat(edge.blocked()).isEqualTo(2L);
    assertThat(edge).isNotEqualTo(BlockEdge.blocker(2L).blocked(1L));
  }
}
