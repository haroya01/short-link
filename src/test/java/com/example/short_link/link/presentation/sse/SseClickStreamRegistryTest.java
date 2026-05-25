package com.example.short_link.link.presentation.sse;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.application.dto.ClickRecordedEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SseClickStreamRegistryTest {

  @Test
  void broadcastsToActiveSubscribersAndIgnoresOtherLinks() throws Exception {
    SseClickStreamRegistry registry = new SseClickStreamRegistry(new SimpleMeterRegistry());
    CountingEmitter watching = new CountingEmitter();
    CountingEmitter otherLink = new CountingEmitter();

    assertThat(registry.register(7L, watching)).isTrue();
    assertThat(registry.register(99L, otherLink)).isTrue();

    registry.onClickRecorded(
        new ClickRecordedEvent(7L, Instant.now(), "KR", "mobile", "kakao.com", false, null));

    assertThat(watching.sent).isEqualTo(1);
    assertThat(otherLink.sent).isZero();
  }

  @Test
  void deadEmitterIsDroppedAfterFailedSend() {
    SseClickStreamRegistry registry = new SseClickStreamRegistry(new SimpleMeterRegistry());
    ThrowingEmitter dead = new ThrowingEmitter();
    CountingEmitter alive = new CountingEmitter();
    registry.register(42L, dead);
    registry.register(42L, alive);

    registry.onClickRecorded(
        new ClickRecordedEvent(42L, Instant.now(), "KR", "desktop", null, false, null));

    assertThat(alive.sent).isEqualTo(1);
    assertThat(registry.activeStreams(42L)).isEqualTo(1);
  }

  @Test
  void rejectsWhenLinkAlreadyHas16Streams() {
    SseClickStreamRegistry registry = new SseClickStreamRegistry(new SimpleMeterRegistry());
    for (int i = 0; i < 16; i++) {
      assertThat(registry.register(11L, new CountingEmitter())).isTrue();
    }
    assertThat(registry.register(11L, new CountingEmitter())).isFalse();
  }

  /** Minimal SseEmitter that lets us count send() calls without Tomcat. */
  private static class CountingEmitter extends SseEmitter {
    int sent = 0;

    CountingEmitter() {
      super(10_000L);
    }

    @Override
    public void send(SseEventBuilder builder) {
      sent++;
    }
  }

  private static class ThrowingEmitter extends SseEmitter {
    ThrowingEmitter() {
      super(10_000L);
    }

    @Override
    public void send(SseEventBuilder builder) throws java.io.IOException {
      throw new java.io.IOException("connection closed");
    }
  }
}
