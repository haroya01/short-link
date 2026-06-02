package com.example.short_link.link.stats.presentation.sse;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.application.dto.ClickRecordedEvent;
import com.example.short_link.link.domain.LinkId;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SseClickStreamRegistryTest {

  @Test
  void broadcastsToActiveSubscribersAndIgnoresOtherLinks() throws Exception {
    SseClickStreamRegistry registry = new SseClickStreamRegistry(new SimpleMeterRegistry());
    CountingEmitter watching = new CountingEmitter();
    CountingEmitter otherLink = new CountingEmitter();

    assertThat(registry.register(new LinkId(7L), watching)).isTrue();
    assertThat(registry.register(new LinkId(99L), otherLink)).isTrue();

    registry.onClickRecorded(
        new ClickRecordedEvent(
            new LinkId(7L), Instant.now(), "KR", "mobile", "kakao.com", false, null));

    assertThat(watching.sent).isEqualTo(1);
    assertThat(otherLink.sent).isZero();
  }

  @Test
  void deadEmitterIsDroppedAfterFailedSend() {
    SseClickStreamRegistry registry = new SseClickStreamRegistry(new SimpleMeterRegistry());
    ThrowingEmitter dead = new ThrowingEmitter();
    CountingEmitter alive = new CountingEmitter();
    registry.register(new LinkId(42L), dead);
    registry.register(new LinkId(42L), alive);

    registry.onClickRecorded(
        new ClickRecordedEvent(new LinkId(42L), Instant.now(), "KR", "desktop", null, false, null));

    assertThat(alive.sent).isEqualTo(1);
    assertThat(registry.activeStreams(new LinkId(42L))).isEqualTo(1);
  }

  @Test
  void rejectsWhenLinkAlreadyHas16Streams() {
    SseClickStreamRegistry registry = new SseClickStreamRegistry(new SimpleMeterRegistry());
    for (int i = 0; i < 16; i++) {
      assertThat(registry.register(new LinkId(11L), new CountingEmitter())).isTrue();
    }
    assertThat(registry.register(new LinkId(11L), new CountingEmitter())).isFalse();
  }

  @Test
  void removeEvictsEmptyBucketButKeepsOthers() {
    SseClickStreamRegistry registry = new SseClickStreamRegistry(new SimpleMeterRegistry());
    CountingEmitter keep = new CountingEmitter();
    CountingEmitter drop = new CountingEmitter();
    registry.register(new LinkId(5L), keep);
    registry.register(new LinkId(5L), drop);

    registry.remove(new LinkId(5L), drop);
    assertThat(registry.activeStreams(new LinkId(5L))).isEqualTo(1);

    registry.remove(new LinkId(5L), keep);
    assertThat(registry.activeStreams(new LinkId(5L))).isZero();
  }

  @Test
  void reRegisterAfterBucketEvictionKeepsNewEmitter() {
    // Pre-fix race: remove() evicted the emptied bucket while register() added to it, orphaning the
    // new emitter. A register following a remove-to-empty must recreate the bucket and retain it.
    SseClickStreamRegistry registry = new SseClickStreamRegistry(new SimpleMeterRegistry());
    CountingEmitter first = new CountingEmitter();
    registry.register(new LinkId(8L), first);
    registry.remove(new LinkId(8L), first);

    CountingEmitter second = new CountingEmitter();
    assertThat(registry.register(new LinkId(8L), second)).isTrue();
    assertThat(registry.activeStreams(new LinkId(8L))).isEqualTo(1);
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
    public void send(SseEventBuilder builder) throws IOException {
      throw new IOException("connection closed");
    }
  }
}
