package com.example.short_link.notification.application.push;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PushRouteTest {

  @Test
  void eachDestinationFillsOnlyItsOwnPlace() {
    assertThat(PushRoute.toPost("yuki", "mika", "their-post", 77L, 41L))
        .isEqualTo(new PushRoute("yuki", "mika", "their-post", null, null, 77L, 41L, null));
    assertThat(PushRoute.toSeries("yuki", "me", "tokyo-walks"))
        .isEqualTo(new PushRoute("yuki", "me", null, "tokyo-walks", null, null, null, null));
    assertThat(PushRoute.toCollection("yuki", 42L))
        .isEqualTo(new PushRoute("yuki", null, null, null, 42L, null, null, null));
    assertThat(PushRoute.toNote("yuki", "me", 5L))
        .isEqualTo(new PushRoute("yuki", "me", null, null, null, null, null, 5L));
    assertThat(PushRoute.toActor("yuki"))
        .isEqualTo(new PushRoute("yuki", null, null, null, null, null, null, null));
  }
}
