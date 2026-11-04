package com.example.short_link.live.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.short_link.common.live.LiveChange;
import com.example.short_link.common.live.LiveRevoke;
import com.example.short_link.common.live.LiveScope;
import com.example.short_link.common.live.LiveTopic;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class LiveStreamsTest {

  private static final Instant NOW = Instant.parse("2026-11-03T01:02:03Z");
  private static final LiveTopic THREAD = LiveTopic.note(10L);
  private static final LiveScope OPEN = new LiveScope(1L, null);

  private LiveStreams streams = streams(500, 4, Duration.ofHours(1));

  @AfterEach
  void stop() {
    streams.shutdown();
  }

  private static LiveStreams streams(int max, int perUser, Duration window) {
    return new LiveStreams(
        new LiveProperties(
            max, perUser, Duration.ofMinutes(5), Duration.ofSeconds(25), window, null, null),
        Clock.fixed(NOW, ZoneOffset.UTC),
        new SimpleMeterRegistry());
  }

  private RecordingChannel watch(long userId, LiveTopic topic, LiveScope scope) {
    RecordingChannel channel = new RecordingChannel();
    LiveSubscriber subscriber = new LiveSubscriber(userId, topic, scope, channel);
    assertThat(streams.register(subscriber)).isTrue();
    streams.start(subscriber, 0L, null);
    return channel;
  }

  private static LiveChange reply(long id) {
    return new LiveChange(THREAD, LiveChange.Kind.REPLY, id, null);
  }

  @Test
  void aBurstInOneWindowReachesEachWatcherOnce() {
    RecordingChannel first = watch(2L, THREAD, OPEN);
    RecordingChannel second = watch(3L, THREAD, OPEN);

    for (long id = 101; id <= 105; id++) {
      streams.offer(reply(id));
    }
    streams.flush(THREAD);

    for (RecordingChannel channel : List.of(first, second)) {
      assertThat(channel.changes()).hasSize(1);
      LiveEvent.Changed changed = channel.changes().getFirst();
      assertThat(changed.items())
          .extracting(LiveEvent.Item::id)
          .containsExactly(101L, 102L, 103L, 104L, 105L);
      assertThat(changed.items()).extracting(LiveEvent.Item::kind).containsOnly("reply");
      assertThat(changed.more()).isFalse();
      assertThat(changed.cursor()).isEqualTo("105");
      assertThat(changed.scope()).isEqualTo("note");
      assertThat(changed.scopeId()).isEqualTo(10L);
      assertThat(changed.at()).isEqualTo(NOW.toString());
    }
  }

  @Test
  void theWorkerFlushesTheWindowOnItsOwn() {
    streams.shutdown();
    streams = streams(500, 4, Duration.ofMillis(50));
    RecordingChannel channel = watch(2L, THREAD, OPEN);

    streams.offer(reply(101));
    streams.offer(reply(102));

    await().atMost(Duration.ofSeconds(5)).until(() -> !channel.changes().isEmpty());
    assertThat(channel.changes()).hasSize(1);
    assertThat(channel.changes().getFirst().items()).hasSize(2);
  }

  @Test
  void aDirectItemNeverFansOutToTheScope() {
    RecordingChannel channel = watch(2L, THREAD, OPEN);

    streams.offer(new LiveChange(THREAD, LiveChange.Kind.REPLY, 101L, null, true));
    streams.flush(THREAD);

    assertThat(channel.changes()).isEmpty();
  }

  @Test
  void aBusierServerWaitsLongerBeforeTellingSoRefetchesStayBounded() {
    streams.shutdown();
    streams =
        new LiveStreams(
            new LiveProperties(
                500,
                4,
                Duration.ofMinutes(5),
                Duration.ofSeconds(25),
                Duration.ofMillis(50),
                Duration.ofMillis(200),
                Duration.ofSeconds(5)),
            Clock.fixed(NOW, ZoneOffset.UTC),
            new SimpleMeterRegistry());
    RecordingChannel watching = watch(2L, THREAD, OPEN);
    for (long id = 20; id < 29; id++) {
      watch(3L + id, LiveTopic.note(id), OPEN);
    }

    streams.offer(reply(101));

    await()
        .during(Duration.ofMillis(700))
        .atMost(Duration.ofSeconds(1))
        .until(() -> watching.changes().isEmpty());
    await().atMost(Duration.ofSeconds(5)).until(() -> !watching.changes().isEmpty());
  }

  @Test
  void aChangeNobodyWatchesIsDroppedAtOnce() {
    RecordingChannel elsewhere = watch(2L, LiveTopic.note(99L), OPEN);

    streams.offer(reply(101));
    streams.flush(THREAD);

    assertThat(elsewhere.changes()).isEmpty();
  }

  @Test
  void aLongBurstNamesTheFirstItemsAndSaysThereIsMore() {
    RecordingChannel channel = watch(2L, THREAD, OPEN);

    for (long id = 1; id <= LiveStreams.MAX_ITEMS + 5; id++) {
      streams.offer(reply(id));
    }
    streams.flush(THREAD);

    LiveEvent.Changed changed = channel.changes().getFirst();
    assertThat(changed.items()).hasSize(LiveStreams.MAX_ITEMS);
    assertThat(changed.more()).isTrue();
    assertThat(changed.cursor()).isEqualTo(String.valueOf(LiveStreams.MAX_ITEMS + 5));
  }

  @Test
  void somethingSaidInARoomReachesOnlyThoseWatchingFromThatRoom() {
    LiveTopic post = LiveTopic.post(5L);
    RecordingChannel outside = watch(2L, post, new LiveScope(1L, null));
    RecordingChannel member = watch(3L, post, new LiveScope(1L, 9L));

    streams.offer(new LiveChange(post, LiveChange.Kind.COMMENT, 70L, 9L));
    streams.flush(post);

    assertThat(outside.changes()).isEmpty();
    assertThat(member.changes()).hasSize(1);
  }

  @Test
  void aReconnectBehindTheLatestIsToldToRefetchRightAfterReady() {
    RecordingChannel channel = new RecordingChannel();
    LiveSubscriber subscriber = new LiveSubscriber(2L, THREAD, OPEN, channel);
    streams.register(subscriber);

    streams.start(subscriber, 120L, 100L);

    assertThat(channel.names()).containsExactly("Ready", "Changed");
    assertThat(((LiveEvent.Ready) channel.sent.getFirst()).cursor()).isEqualTo("120");
    assertThat(channel.changes().getFirst().more()).isTrue();
  }

  @Test
  void aReconnectThatMissedNothingOnlyHearsReady() {
    RecordingChannel channel = new RecordingChannel();
    LiveSubscriber subscriber = new LiveSubscriber(2L, THREAD, OPEN, channel);
    streams.register(subscriber);

    streams.start(subscriber, 120L, 120L);

    assertThat(channel.names()).containsExactly("Ready");
  }

  @Test
  void aChangeBetweenJoiningAndReadyIsNotLost() {
    RecordingChannel channel = new RecordingChannel();
    LiveSubscriber subscriber = new LiveSubscriber(2L, THREAD, OPEN, channel);
    streams.register(subscriber);
    streams.offer(reply(130L));
    streams.flush(THREAD);

    streams.start(subscriber, 120L, null);

    assertThat(channel.names()).containsExactly("Ready", "Changed");
    assertThat(((LiveEvent.Ready) channel.sent.getFirst()).cursor()).isEqualTo("130");
  }

  @Test
  void aFifthStreamCrowdsOutThatPersonsOldestInsteadOfBeingRefused() {
    RecordingChannel oldest = watch(2L, LiveTopic.note(0L), OPEN);
    for (long id = 1; id < 4; id++) {
      watch(2L, LiveTopic.note(id), OPEN);
    }

    assertThat(streams.admits(2L)).isTrue();
    RecordingChannel newest = watch(2L, LiveTopic.note(9L), OPEN);

    await().atMost(Duration.ofSeconds(5)).until(() -> oldest.closed);
    assertThat(oldest.names()).containsExactly("Ready", "Busy");
    assertThat(((LiveEvent.Busy) oldest.sent.getLast()).retryAfterSeconds()).isEqualTo(60);
    assertThat(streams.open()).isEqualTo(4);
    streams.offer(new LiveChange(LiveTopic.note(0L), LiveChange.Kind.REPLY, 5L, null));
    streams.flush(LiveTopic.note(0L));
    assertThat(oldest.changes()).isEmpty();
    assertThat(newest.closed).isFalse();
  }

  @Test
  void theServerHoldsAtMostItsShare() {
    streams.shutdown();
    streams = streams(2, 4, Duration.ofHours(1));
    watch(2L, THREAD, OPEN);
    watch(3L, THREAD, OPEN);

    assertThat(streams.admits(4L)).isFalse();
    assertThat(streams.register(new LiveSubscriber(4L, THREAD, OPEN, new RecordingChannel())))
        .isFalse();
    assertThat(streams.open()).isEqualTo(2);
  }

  @Test
  void aFullServerStillLetsSomeoneSwapTheirOwnStreams() {
    streams.shutdown();
    streams = streams(2, 2, Duration.ofHours(1));
    watch(2L, THREAD, OPEN);
    watch(2L, LiveTopic.note(11L), OPEN);

    assertThat(streams.admits(2L)).isTrue();
    assertThat(streams.register(new LiveSubscriber(2L, THREAD, OPEN, new RecordingChannel())))
        .isTrue();
    assertThat(streams.open()).isEqualTo(2);
  }

  @Test
  void zeroStreamsSendsEveryoneBackToRefreshing() {
    streams.shutdown();
    streams = streams(0, 4, Duration.ofHours(1));

    assertThat(streams.admits(2L)).isFalse();
  }

  @Test
  void leavingFreesTheSlot() {
    RecordingChannel channel = new RecordingChannel();
    LiveSubscriber subscriber = new LiveSubscriber(2L, THREAD, OPEN, channel);
    streams.register(subscriber);

    streams.remove(subscriber);
    streams.remove(subscriber);

    assertThat(streams.open()).isZero();
    streams.offer(reply(101));
    streams.flush(THREAD);
    assertThat(channel.changes()).isEmpty();
  }

  @Test
  void theHeartbeatReachesEveryReadyStream() {
    RecordingChannel channel = watch(2L, THREAD, OPEN);

    streams.beat();

    assertThat(channel.names()).containsExactly("Ready", "Heartbeat");
  }

  @Test
  void aStreamThatCannotBeWrittenIsDroppedAtOnce() {
    RecordingChannel channel = watch(2L, THREAD, OPEN);
    channel.broken = true;

    streams.beat();

    assertThat(channel.closed).isTrue();
    assertThat(streams.open()).isZero();
  }

  @Test
  void aClosedScopeIsToldItIsGoneAndEnded() {
    RecordingChannel watching = watch(2L, THREAD, OPEN);
    RecordingChannel other = watch(3L, LiveTopic.note(11L), OPEN);

    streams.revoke(new LiveRevoke.Closed(THREAD));

    await().atMost(Duration.ofSeconds(5)).until(() -> watching.closed);
    assertThat(watching.names()).containsExactly("Ready", "Revoked");
    assertThat(other.closed).isFalse();
    assertThat(streams.open()).isEqualTo(1);
  }

  @Test
  void aRecheckEndsTheStreamWithoutSayingAccessIsGone() {
    RecordingChannel watching = watch(2L, THREAD, OPEN);

    streams.revoke(new LiveRevoke.Recheck(THREAD));

    await().atMost(Duration.ofSeconds(5)).until(() -> watching.closed);
    assertThat(watching.names()).containsExactly("Ready");
  }

  @Test
  void aPausedHubStartsAgainWithItsHeartbeat() {
    streams.shutdown();
    streams =
        new LiveStreams(
            new LiveProperties(
                500, 4, Duration.ofMinutes(5), Duration.ofMillis(50), null, null, null),
            Clock.fixed(NOW, ZoneOffset.UTC),
            new SimpleMeterRegistry());
    streams.start();
    streams.stop();

    streams.start();
    RecordingChannel channel = watch(2L, THREAD, OPEN);

    assertThat(streams.isRunning()).isTrue();
    await().atMost(Duration.ofSeconds(5)).until(() -> channel.names().contains("Heartbeat"));
  }

  @Test
  void stoppingClosesEveryStream() {
    RecordingChannel first = watch(2L, THREAD, OPEN);
    RecordingChannel second = watch(3L, LiveTopic.room(4L), new LiveScope(null, 4L));

    streams.stop();

    assertThat(first.closed).isTrue();
    assertThat(second.closed).isTrue();
    assertThat(streams.open()).isZero();
  }
}
