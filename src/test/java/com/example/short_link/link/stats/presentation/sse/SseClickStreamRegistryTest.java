package com.example.short_link.link.stats.presentation.sse;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.application.dto.ClickRecordedEvent;
import com.example.short_link.link.domain.LinkId;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SseClickStreamRegistryTest {

  @Test
  void broadcastsToActiveSubscribersAndIgnoresOtherLinks() throws Exception {
    SseClickStreamRegistry registry =
        new SseClickStreamRegistry(new SimpleMeterRegistry(), Runnable::run);
    CountingEmitter watching = new CountingEmitter();
    CountingEmitter otherLink = new CountingEmitter();

    assertThat(registry.register(new LinkId(7L), watching)).isTrue();
    assertThat(registry.register(new LinkId(99L), otherLink)).isTrue();

    registry.onClickRecorded(
        new ClickRecordedEvent(
            new LinkId(7L),
            "abc1234",
            null,
            Instant.now(),
            "KR",
            "mobile",
            "kakao.com",
            false,
            null));

    assertThat(watching.sent).isEqualTo(1);
    assertThat(watching.payload.get("channel")).isEqualTo("kakao.com");
    assertThat(watching.payload.containsKey("referrerHost")).isFalse();
    assertThat(otherLink.sent).isZero();
  }

  @Test
  void ownerChannelReceivesOwnClicksOnly() {
    SseClickStreamRegistry registry =
        new SseClickStreamRegistry(new SimpleMeterRegistry(), Runnable::run);
    CountingEmitter owner = new CountingEmitter();
    CountingEmitter stranger = new CountingEmitter();
    assertThat(registry.registerForUser(1L, owner)).isTrue();
    assertThat(registry.registerForUser(2L, stranger)).isTrue();

    registry.onClickRecorded(
        new ClickRecordedEvent(
            new LinkId(7L),
            "abc1234",
            1L,
            Instant.now(),
            "KR",
            "mobile",
            "kakao.com",
            false,
            null));

    assertThat(owner.sent).isEqualTo(1);
    assertThat(owner.payload.get("channel")).isEqualTo("kakao.com");
    assertThat(owner.payload.containsKey("referrerHost")).isFalse();
    assertThat(stranger.sent).isZero();
  }

  @Test
  void anonymousLinkClickSkipsOwnerChannel() {
    SseClickStreamRegistry registry =
        new SseClickStreamRegistry(new SimpleMeterRegistry(), Runnable::run);
    CountingEmitter owner = new CountingEmitter();
    registry.registerForUser(1L, owner);

    registry.onClickRecorded(
        new ClickRecordedEvent(
            new LinkId(7L), "abc1234", null, Instant.now(), "KR", "mobile", null, false, null));

    assertThat(owner.sent).isZero();
  }

  @Test
  void deadEmitterIsDroppedAfterFailedSend() {
    SseClickStreamRegistry registry =
        new SseClickStreamRegistry(new SimpleMeterRegistry(), Runnable::run);
    ThrowingEmitter dead = new ThrowingEmitter();
    CountingEmitter alive = new CountingEmitter();
    registry.register(new LinkId(42L), dead);
    registry.register(new LinkId(42L), alive);

    registry.onClickRecorded(
        new ClickRecordedEvent(
            new LinkId(42L), "abc1234", null, Instant.now(), "KR", "desktop", null, false, null));

    assertThat(alive.sent).isEqualTo(1);
    assertThat(registry.activeStreams(new LinkId(42L))).isEqualTo(1);
  }

  @Test
  void rejectsWhenLinkAlreadyHas16Streams() {
    SseClickStreamRegistry registry =
        new SseClickStreamRegistry(new SimpleMeterRegistry(), Runnable::run);
    for (int i = 0; i < 16; i++) {
      assertThat(registry.register(new LinkId(11L), new CountingEmitter())).isTrue();
    }
    assertThat(registry.register(new LinkId(11L), new CountingEmitter())).isFalse();
  }

  @Test
  void removeEvictsEmptyBucketButKeepsOthers() {
    SseClickStreamRegistry registry =
        new SseClickStreamRegistry(new SimpleMeterRegistry(), Runnable::run);
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
    SseClickStreamRegistry registry =
        new SseClickStreamRegistry(new SimpleMeterRegistry(), Runnable::run);
    CountingEmitter first = new CountingEmitter();
    registry.register(new LinkId(8L), first);
    registry.remove(new LinkId(8L), first);

    CountingEmitter second = new CountingEmitter();
    assertThat(registry.register(new LinkId(8L), second)).isTrue();
    assertThat(registry.activeStreams(new LinkId(8L))).isEqualTo(1);
  }

  @Test
  void aClientThatStopsReadingHoldsNeitherTheClickFlushNorTheScheduler() throws Exception {
    SseClickStreamRegistry registry = new SseClickStreamRegistry(new SimpleMeterRegistry());
    CountDownLatch reads = new CountDownLatch(1);
    try {
      registry.register(new LinkId(7L), new StuckEmitter(reads));
      registry.registerForUser(1L, new StuckEmitter(reads));
      ClickRecordedEvent click =
          new ClickRecordedEvent(
              new LinkId(7L), "abc1234", 1L, Instant.now(), "KR", "mobile", null, false, null);

      assertThat(CompletableFuture.runAsync(() -> registry.onClickRecorded(click)))
          .succeedsWithin(Duration.ofSeconds(2));
      assertThat(CompletableFuture.runAsync(registry::heartbeat))
          .succeedsWithin(Duration.ofSeconds(2));
    } finally {
      reads.countDown();
      registry.shutdown();
    }
  }

  @Test
  void sendsBeyondTheQueueAreDroppedAndCounted() {
    SimpleMeterRegistry meters = new SimpleMeterRegistry();
    SseClickStreamRegistry registry =
        new SseClickStreamRegistry(
            meters,
            work -> {
              throw new RejectedExecutionException("full");
            });
    CountingEmitter watching = new CountingEmitter();
    registry.register(new LinkId(7L), watching);

    registry.onClickRecorded(
        new ClickRecordedEvent(
            new LinkId(7L), "abc1234", null, Instant.now(), "KR", "mobile", null, false, null));
    registry.heartbeat();

    assertThat(watching.sent).isZero();
    assertThat(meters.counter("sse.click_stream.dropped").count()).isEqualTo(2);
  }

  @Test
  void heartbeatKeepsEveryLinkAndAccountStreamTalkingAndDropsDeadOnes() {
    SseClickStreamRegistry registry =
        new SseClickStreamRegistry(new SimpleMeterRegistry(), Runnable::run);
    FrameEmitter link = new FrameEmitter();
    FrameEmitter account = new FrameEmitter();
    registry.register(new LinkId(3L), link);
    registry.register(new LinkId(3L), new ThrowingEmitter());
    registry.registerForUser(1L, account);

    registry.heartbeat();

    assertThat(link.frames).containsExactly(":\n\n");
    assertThat(account.frames).containsExactly(":\n\n");
    assertThat(registry.activeStreams(new LinkId(3L))).isEqualTo(1);
  }

  private static class FrameEmitter extends SseEmitter {
    final List<String> frames = new ArrayList<>();

    FrameEmitter() {
      super(10_000L);
    }

    @Override
    public void send(SseEventBuilder builder) {
      frames.add(
          builder.build().stream()
              .map(item -> String.valueOf(item.getData()))
              .collect(Collectors.joining()));
    }
  }

  private static class CountingEmitter extends SseEmitter {
    int sent = 0;
    Map<?, ?> payload;

    CountingEmitter() {
      super(10_000L);
    }

    @Override
    public void send(SseEventBuilder builder) {
      sent++;
      payload =
          builder.build().stream()
              .map(item -> item.getData())
              .filter(Map.class::isInstance)
              .map(Map.class::cast)
              .findFirst()
              .orElseThrow();
    }
  }

  private static class StuckEmitter extends SseEmitter {
    private final CountDownLatch reads;

    StuckEmitter(CountDownLatch reads) {
      super(10_000L);
      this.reads = reads;
    }

    @Override
    public void send(SseEventBuilder builder) {
      try {
        reads.await();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
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
