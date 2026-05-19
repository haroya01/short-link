package com.example.short_link.admin.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LinkMetricsRecorderTest {

  @Test
  void aggregateComputesPercentilesAndErrorRate() {
    LinkMetricsRecorder recorder = new LinkMetricsRecorder();
    String code = "abc123";
    long[] latencies = {10, 20, 30, 40, 50, 60, 70, 80, 90, 100};
    for (long ms : latencies) recorder.record(code, ms, "redirect");
    recorder.record(code, 999, "not_found");
    recorder.record(code, 999, "blocked");

    var snap = recorder.snapshot(60);
    assertThat(snap).hasSize(1);
    LinkMetricsRecorder.Aggregate agg = snap.get(0).getValue();
    assertThat(agg.count()).isEqualTo(12);
    assertThat(agg.p50Millis()).isEqualTo(60); // ceil(0.5*12)=6 → idx 5 → 60
    assertThat(agg.p95Millis()).isEqualTo(999);
    assertThat(agg.p99Millis()).isEqualTo(999);
    assertThat(agg.outcomeCounts())
        .containsEntry("redirect", 10L)
        .containsEntry("not_found", 1L)
        .containsEntry("blocked", 1L);
    // 2 errors out of 12 samples
    assertThat(agg.errorRate()).isEqualTo(2.0 / 12.0);
  }

  @Test
  void emptySliceReturnsEmptyMap() {
    LinkMetricsRecorder recorder = new LinkMetricsRecorder();
    assertThat(recorder.sliceWindow(60)).isEmpty();
  }

  @Test
  void evictsLeastRecentlyUsedOnceCapHit() {
    LinkMetricsRecorder recorder = new LinkMetricsRecorder();
    for (int i = 0; i < LinkMetricsRecorder.MAX_TRACKED_LINKS + 50; i++) {
      recorder.record("c" + i, 1, "redirect");
    }
    // capped at MAX_TRACKED_LINKS — older codes evicted
    assertThat(recorder.trackedSize()).isEqualTo(LinkMetricsRecorder.MAX_TRACKED_LINKS);
  }

  @Test
  void windowExcludesOldSamples() {
    MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    LinkMetricsRecorder recorder = new LinkMetricsRecorder(clock);
    recorder.record("old", 10, "redirect");
    clock.advanceMinutes(120);
    recorder.record("recent", 20, "redirect");

    Map<String, List<LinkMetricsRecorder.Sample>> hour = recorder.sliceWindow(60);
    assertThat(hour).containsOnlyKeys("recent");

    Map<String, List<LinkMetricsRecorder.Sample>> wider = recorder.sliceWindow(60 * 24);
    assertThat(wider).containsKeys("old", "recent");
  }

  @Test
  void purgesBucketsOlderThanMaxAge() {
    MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    LinkMetricsRecorder recorder = new LinkMetricsRecorder(clock);
    recorder.record("ghost", 5, "redirect");
    assertThat(recorder.trackedSize()).isEqualTo(1);
    clock.advanceMinutes(LinkMetricsRecorder.MAX_AGE_MINUTES + 1);
    recorder.sliceWindow(60); // triggers purge on read
    assertThat(recorder.trackedSize()).isZero();
  }

  @Test
  void ringOverwritesOldestWhenFull() {
    LinkMetricsRecorder recorder = new LinkMetricsRecorder();
    String code = "spin";
    for (int i = 0; i < LinkMetricsRecorder.SAMPLES_PER_LINK + 50; i++) {
      recorder.record(code, i, "redirect");
    }
    var snap = recorder.snapshot(60);
    assertThat(snap).hasSize(1);
    // ring capacity caps the snapshot size
    assertThat(snap.get(0).getValue().count()).isEqualTo(LinkMetricsRecorder.SAMPLES_PER_LINK);
  }

  @Test
  void nullOrBlankShortCodeIsIgnored() {
    LinkMetricsRecorder recorder = new LinkMetricsRecorder();
    recorder.record(null, 10, "redirect");
    recorder.record("", 10, "redirect");
    recorder.record("  ", 10, "redirect");
    assertThat(recorder.trackedSize()).isZero();
  }

  @Test
  void nullOutcomeFallsBackToOther() {
    LinkMetricsRecorder recorder = new LinkMetricsRecorder();
    recorder.record("x1", 5, null);
    var snap = recorder.snapshot(60);
    assertThat(snap.get(0).getValue().outcomeCounts()).containsEntry("other", 1L);
  }

  static final class MutableClock extends Clock {
    private Instant now;

    MutableClock(Instant start) {
      this.now = start;
    }

    void advanceMinutes(int minutes) {
      this.now = this.now.plusSeconds(minutes * 60L);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
