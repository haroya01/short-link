package com.example.short_link.user.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FollowEdgeTest {

  @Test
  void theNamedStepsPutEachIdInItsRole() {
    FollowEdge edge = FollowEdge.follower(1L).following(2L);

    assertThat(edge.follower()).isEqualTo(1L);
    assertThat(edge.following()).isEqualTo(2L);
    assertThat(edge).isNotEqualTo(FollowEdge.follower(2L).following(1L));
  }

  @Test
  void anEntityTakesItsColumnsFromTheEdge() {
    FollowEntity follow = new FollowEntity(FollowEdge.follower(1L).following(2L), 7L);

    assertThat(follow.getFollowerId()).isEqualTo(1L);
    assertThat(follow.getFollowingId()).isEqualTo(2L);
    assertThat(follow.getSourcePostId()).isEqualTo(7L);
  }
}
