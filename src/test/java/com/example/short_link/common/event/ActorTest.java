package com.example.short_link.common.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ActorTest {

  @Test
  void anActorIsAMemberOrAnAccountElsewhereNeverBoth() {
    assertThat(Actor.member(2L)).isEqualTo(new Actor(2L, null));
    assertThat(Actor.remote(7L)).isEqualTo(new Actor(null, 7L));
    assertThatThrownBy(() -> new Actor(2L, 7L)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void onlyNobodyIsNone() {
    assertThat(Actor.NONE.isNone()).isTrue();
    assertThat(Actor.member(null)).isEqualTo(Actor.NONE);
    assertThat(Actor.member(2L).isNone()).isFalse();
    assertThat(Actor.remote(7L).isNone()).isFalse();
  }
}
