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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class LiveStreamsTest {

  private static final Instant NOW = Instant.parse("2026-11-03T01:02:03Z");
  private static final LiveTopic THREAD = LiveTopic.note(10L);
  private static final LiveScope OPEN = new LiveScope(1L, null);

  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private LiveStreams streams = streams(500, 4, Duration.ofHours(1));

  @AfterEach
  void stop() {
    streams.shutdown();
  }

  private LiveStreams streams(int max, int perUser, Duration window) {
    return new LiveStreams(
        new LiveProperties(
            max, perUser, Duration.ofMinutes(5), Duration.ofSeconds(25), window, null, null, null),
        Clock.fixed(NOW, ZoneOffset.UTC),
        meters);
  }

  private double counted(String name, String... tags) {
    var counter = meters.find(name).tags(tags).counter();
    return counter == null ? 0 : counter.count();
  }

  private RecordingChannel watch(long userId, LiveTopic topic, LiveScope scope) {
    RecordingChannel channel = new RecordingChannel();
    LiveSubscriber subscriber = new LiveSubscriber(userId, topic, scope, channel);
    assertThat(streams.register(subscriber)).isTrue();
    streams.start(subscriber, 0L, null, "renewed");
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
                Duration.ofSeconds(5),
                null),
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

    streams.start(subscriber, 120L, 100L, "renewed");

    assertThat(channel.names()).containsExactly("Ready", "Changed");
    assertThat(((LiveEvent.Ready) channel.sent.getFirst()).cursor()).isEqualTo("120");
    assertThat(channel.changes().getFirst().more()).isTrue();
  }

  @Test
  void aReconnectThatMissedNothingOnlyHearsReady() {
    RecordingChannel channel = new RecordingChannel();
    LiveSubscriber subscriber = new LiveSubscriber(2L, THREAD, OPEN, channel);
    streams.register(subscriber);

    streams.start(subscriber, 120L, 120L, "renewed");

    assertThat(channel.names()).containsExactly("Ready");
  }

  @Test
  void aChangeBetweenJoiningAndReadyIsNotLost() {
    RecordingChannel channel = new RecordingChannel();
    LiveSubscriber subscriber = new LiveSubscriber(2L, THREAD, OPEN, channel);
    streams.register(subscriber);
    streams.offer(reply(130L));
    streams.flush(THREAD);

    streams.start(subscriber, 120L, null, "renewed");

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
                500, 4, Duration.ofMinutes(5), Duration.ofMillis(50), null, null, null, null),
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
  void readyHandsBackAFreshTokenAndASpreadReconnectDelay() {
    streams.shutdown();
    streams = drawing(0.5, Clock.fixed(NOW, ZoneOffset.UTC));

    RecordingChannel channel = watch(2L, THREAD, OPEN);

    LiveEvent.Ready ready = (LiveEvent.Ready) channel.sent.getFirst();
    assertThat(ready.streamToken()).isEqualTo("renewed");
    assertThat(ready.retryMs()).isEqualTo(3_000L);
  }

  @Test
  void eachStreamEndsAtItsOwnTimeWithAByeAndARotateDelay() {
    streams.shutdown();
    double[] draws = {0.0, 0.0, 1.0, 0.0};
    int[] next = {0};
    streams =
        new LiveStreams(
            new LiveProperties(
                500,
                4,
                Duration.ofMillis(500),
                Duration.ofHours(1),
                Duration.ofHours(1),
                null,
                null,
                null),
            Clock.fixed(NOW, ZoneOffset.UTC),
            new SimpleMeterRegistry(),
            () -> next[0] < draws.length ? draws[next[0]++] : 1.0);
    RecordingChannel early = watch(2L, THREAD, OPEN);
    RecordingChannel late = watch(3L, THREAD, OPEN);

    await().atMost(Duration.ofSeconds(5)).until(() -> early.closed);
    assertThat(late.closed).isFalse();
    assertThat(early.names()).containsExactly("Ready", "Bye");

    await().atMost(Duration.ofSeconds(5)).until(() -> late.closed);
    LiveEvent.Bye bye = (LiveEvent.Bye) late.sent.getLast();
    assertThat(bye.reason()).isEqualTo("rotate");
    assertThat(bye.retryMs()).isEqualTo(5_000L);
    assertThat(streams.open()).isZero();
  }

  @Test
  void aStreamThatLeavesFirstIsNotRotatedLater() {
    streams.shutdown();
    streams =
        new LiveStreams(
            new LiveProperties(
                500, 4, Duration.ofMillis(200), null, Duration.ofHours(1), null, null, null),
            Clock.fixed(NOW, ZoneOffset.UTC),
            new SimpleMeterRegistry(),
            () -> 0.5);
    RecordingChannel channel = new RecordingChannel();
    LiveSubscriber subscriber = new LiveSubscriber(2L, THREAD, OPEN, channel);
    streams.register(subscriber);
    streams.start(subscriber, 0L, null, "renewed");

    streams.remove(subscriber);

    await()
        .during(Duration.ofMillis(500))
        .atMost(Duration.ofSeconds(1))
        .until(() -> !channel.closed);
    assertThat(channel.names()).containsExactly("Ready");
  }

  @Test
  void aRestartSendsEveryoneAwayWithDelaysSpreadOverAMinute() {
    streams.shutdown();
    streams = drawing(0.25, Clock.fixed(NOW, ZoneOffset.UTC));
    RecordingChannel first = watch(2L, THREAD, OPEN);
    RecordingChannel second = watch(3L, LiveTopic.room(4L), new LiveScope(null, 4L));

    streams.stop();

    for (RecordingChannel channel : List.of(first, second)) {
      LiveEvent.Bye bye = (LiveEvent.Bye) channel.sent.getLast();
      assertThat(bye.reason()).isEqualTo("restart");
      assertThat(bye.retryMs()).isEqualTo(30_000L);
      assertThat(channel.closed).isTrue();
    }
  }

  private static LiveStreams drawing(double draw, Clock clock) {
    return new LiveStreams(
        new LiveProperties(500, 4, null, null, Duration.ofHours(1), null, null, null),
        clock,
        new SimpleMeterRegistry(),
        () -> draw);
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

  @Test
  void everyWayAStreamEndsIsCountedByWhy() {
    streams.shutdown();
    streams = streams(2, 1, Duration.ofHours(1));
    RecordingChannel crowded = watch(2L, THREAD, OPEN);
    RecordingChannel lasting = watch(3L, THREAD, OPEN);
    assertThat(streams.register(new LiveSubscriber(4L, THREAD, OPEN, new RecordingChannel())))
        .isFalse();
    RecordingChannel closed = watch(2L, LiveTopic.note(11L), OPEN);
    await().atMost(Duration.ofSeconds(5)).until(() -> crowded.closed);

    streams.revoke(new LiveRevoke.Closed(LiveTopic.note(11L)));
    await().atMost(Duration.ofSeconds(5)).until(() -> closed.closed);
    RecordingChannel rechecked = watch(2L, LiveTopic.note(12L), OPEN);
    streams.revoke(new LiveRevoke.Recheck(LiveTopic.note(12L)));
    await().atMost(Duration.ofSeconds(5)).until(() -> rechecked.closed);
    streams.stop();

    assertThat(lasting.names()).last().isEqualTo("Bye");
    assertThat(counted("live.streams.rejected")).isEqualTo(1);
    assertThat(counted("live.streams.crowded-out")).isEqualTo(1);
    assertThat(counted("live.streams.ended", "revoked", "true")).isEqualTo(1);
    assertThat(counted("live.streams.ended", "revoked", "false")).isEqualTo(1);
    assertThat(counted("live.streams.bye", "reason", "restart")).isEqualTo(1);
  }

  @Test
  void onlyWhatReachedAStreamIsCountedAsDelivered() {
    RecordingChannel healthy = watch(2L, THREAD, OPEN);
    RecordingChannel alsoHealthy = watch(4L, THREAD, OPEN);
    RecordingChannel broken = watch(3L, THREAD, OPEN);
    broken.broken = true;

    streams.offer(reply(101));
    streams.flush(THREAD);

    assertThat(healthy.changes()).hasSize(1);
    assertThat(alsoHealthy.changes()).hasSize(1);
    assertThat(broken.closed).isTrue();
    assertThat(counted("live.changes.delivered")).isEqualTo(2);
  }

  @Test
  void whenOneOfTwoWatchersLeavesTheOtherStillHearsTheTopic() {
    RecordingChannel staying = new RecordingChannel();
    LiveSubscriber stays = new LiveSubscriber(2L, THREAD, OPEN, staying);
    LiveSubscriber leaves = new LiveSubscriber(3L, THREAD, OPEN, new RecordingChannel());
    streams.register(stays);
    streams.start(stays, 0L, null, "renewed");
    streams.register(leaves);
    streams.start(leaves, 0L, null, "renewed");

    streams.remove(leaves);
    streams.offer(reply(101));
    streams.flush(THREAD);

    assertThat(staying.changes()).hasSize(1);
  }

  @Test
  void aPersonsRemainingStreamStillCountsTowardTheirShareAfterAnotherLeaves() {
    RecordingChannel remaining = watch(2L, LiveTopic.note(0L), OPEN);
    LiveSubscriber leaving =
        new LiveSubscriber(2L, LiveTopic.note(1L), OPEN, new RecordingChannel());
    streams.register(leaving);
    streams.remove(leaving);
    for (long id = 2; id < 5; id++) {
      watch(2L, LiveTopic.note(id), OPEN);
    }
    assertThat(remaining.closed).isFalse();

    watch(2L, LiveTopic.note(5L), OPEN);

    await().atMost(Duration.ofSeconds(5)).until(() -> remaining.closed);
    assertThat(streams.open()).isEqualTo(4);
  }

  @Test
  void askingWhetherSomeoneFitsOrStoppingLeavesAdmissionOpenForOtherThreads() throws Exception {
    streams.admits(2L);
    assertThat(
            CompletableFuture.supplyAsync(
                () ->
                    streams.register(new LiveSubscriber(2L, THREAD, OPEN, new RecordingChannel()))))
        .succeedsWithin(Duration.ofSeconds(2))
        .isEqualTo(true);

    streams.stop();
    assertThat(CompletableFuture.supplyAsync(() -> streams.admits(3L)))
        .succeedsWithin(Duration.ofSeconds(2));
  }

  @Test
  void aRotationArrivingAfterTheStreamEndedIsCancelledAtOnceAndEndingCancelsOneAlreadySet()
      throws Exception {
    ScheduledExecutorService clockwork = Executors.newSingleThreadScheduledExecutor();
    try {
      LiveSubscriber late = new LiveSubscriber(2L, THREAD, OPEN, new RecordingChannel());
      late.end();
      ScheduledFuture<?> tooLate = clockwork.schedule(() -> {}, 1, TimeUnit.HOURS);
      late.rotation(tooLate);
      assertThat(late.ended()).isTrue();
      assertThat(tooLate.isCancelled()).isTrue();

      LiveSubscriber early = new LiveSubscriber(2L, THREAD, OPEN, new RecordingChannel());
      ScheduledFuture<?> set = clockwork.schedule(() -> {}, 1, TimeUnit.HOURS);
      early.rotation(set);
      assertThat(set.isCancelled()).isFalse();
      early.end();
      assertThat(set.isCancelled()).isTrue();
    } finally {
      clockwork.shutdownNow();
    }
  }

  @Test
  void theHubRunsOnlyBetweenStartAndStopAndShuttingDownSaysGoodbye() {
    assertThat(streams.isRunning()).isFalse();
    streams.start();
    assertThat(streams.isRunning()).isTrue();
    RecordingChannel open = watch(2L, THREAD, OPEN);

    streams.shutdown();

    assertThat(streams.isRunning()).isFalse();
    assertThat(open.names()).last().isEqualTo("Bye");
    assertThat(open.closed).isTrue();
  }
}
