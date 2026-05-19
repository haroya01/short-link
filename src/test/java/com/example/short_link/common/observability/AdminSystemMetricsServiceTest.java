package com.example.short_link.common.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class AdminSystemMetricsServiceTest {

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final AdminSystemMetricsService service = new AdminSystemMetricsService(registry);

  @Test
  void snapshotReadsRegisteredGauges() {
    registry.gauge("jvm.memory.used", Tags.of(Tag.of("area", "heap")), 256_000_000L);
    registry.gauge("jvm.memory.max", Tags.of(Tag.of("area", "heap")), 1_024_000_000L);
    registry.gauge("jvm.memory.used", Tags.of(Tag.of("area", "nonheap")), 64_000_000L);
    registry.gauge("jvm.threads.live", 24);
    registry.gauge("jvm.threads.daemon", 12);
    registry.gauge("hikaricp.connections.active", 3);
    registry.gauge("hikaricp.connections.idle", 7);
    registry.gauge("hikaricp.connections.pending", 0);
    registry.gauge("hikaricp.connections", 10);

    AdminSystemMetricsService.SystemMetrics snap = service.snapshot();

    assertThat(snap.jvm().heapUsedBytes()).isEqualTo(256_000_000L);
    assertThat(snap.jvm().heapMaxBytes()).isEqualTo(1_024_000_000L);
    assertThat(snap.jvm().nonHeapUsedBytes()).isEqualTo(64_000_000L);
    assertThat(snap.jvm().threadsLive()).isEqualTo(24);
    assertThat(snap.jvm().threadsDaemon()).isEqualTo(12);
    assertThat(snap.hikari().active()).isEqualTo(3);
    assertThat(snap.hikari().idle()).isEqualTo(7);
    assertThat(snap.hikari().total()).isEqualTo(10);
  }

  @Test
  void missingGaugesReturnZeroWithoutThrowing() {
    AdminSystemMetricsService.SystemMetrics snap = service.snapshot();
    assertThat(snap.jvm().heapUsedBytes()).isZero();
    assertThat(snap.hikari().active()).isZero();
    assertThat(snap.caches()).isEmpty();
  }

  @Test
  void cachesAggregateHitsAndMissesPerCacheName() {
    // Simulates what Micrometer's CacheMeterBinder emits — two counters per cache, keyed on
    // result=hit|miss.
    registry.counter("cache.gets", "cache", "link", "result", "hit").increment(80);
    registry.counter("cache.gets", "cache", "link", "result", "miss").increment(20);
    registry.counter("cache.gets", "cache", "public-profile", "result", "hit").increment(45);
    registry.counter("cache.gets", "cache", "public-profile", "result", "miss").increment(15);

    var caches = service.snapshot().caches();

    assertThat(caches).containsOnlyKeys("link", "public-profile");
    assertThat(caches.get("link").hits()).isEqualTo(80L);
    assertThat(caches.get("link").misses()).isEqualTo(20L);
    assertThat(caches.get("link").hitRatio()).isCloseTo(0.8, within(0.0001));
    assertThat(caches.get("public-profile").hitRatio()).isCloseTo(0.75, within(0.0001));
  }

  @Test
  void cacheHitRatioIsZeroWhenNoSamples() {
    assertThat(new AdminSystemMetricsService.CacheStat(0, 0).hitRatio()).isZero();
  }

  @Test
  void outboundHttpAggregatesPerClient() {
    // Simulates what LinkWebhookDispatcher / OgScraper emit — one timer per (client, result)
    // tuple, sample is the wall-clock latency.
    registry
        .timer("outbound.http", "client", "webhook", "result", "ok")
        .record(50, java.util.concurrent.TimeUnit.MILLISECONDS);
    registry
        .timer("outbound.http", "client", "webhook", "result", "ok")
        .record(150, java.util.concurrent.TimeUnit.MILLISECONDS);
    registry
        .timer("outbound.http", "client", "webhook", "result", "non_2xx")
        .record(200, java.util.concurrent.TimeUnit.MILLISECONDS);
    registry
        .timer("outbound.http", "client", "og_fetch", "result", "ok")
        .record(800, java.util.concurrent.TimeUnit.MILLISECONDS);

    var outbound = service.snapshot().outboundHttp();

    assertThat(outbound).containsOnlyKeys("webhook", "og_fetch");
    var wh = outbound.get("webhook");
    assertThat(wh.count()).isEqualTo(3L);
    assertThat(wh.meanMillis()).isCloseTo((50.0 + 150.0 + 200.0) / 3.0, within(0.5));
    assertThat(wh.resultCounts()).containsEntry("ok", 2L).containsEntry("non_2xx", 1L);

    var og = outbound.get("og_fetch");
    assertThat(og.count()).isEqualTo(1L);
    assertThat(og.maxMillis()).isCloseTo(800.0, within(1.0));
  }

  @Test
  void outboundHttpIsEmptyWhenNoTimersRegistered() {
    assertThat(service.snapshot().outboundHttp()).isEmpty();
  }

  @Test
  void scheduledTasksAggregatePerTask() {
    registry
        .timer("scheduled.task", "task", "OgRefreshJob.run", "result", "ok")
        .record(120, java.util.concurrent.TimeUnit.MILLISECONDS);
    registry
        .timer("scheduled.task", "task", "OgRefreshJob.run", "result", "ok")
        .record(80, java.util.concurrent.TimeUnit.MILLISECONDS);
    registry
        .timer("scheduled.task", "task", "OgRefreshJob.run", "result", "error")
        .record(450, java.util.concurrent.TimeUnit.MILLISECONDS);
    registry
        .timer("scheduled.task", "task", "RequestMetricsRecorder.flush", "result", "ok")
        .record(15, java.util.concurrent.TimeUnit.MILLISECONDS);

    var tasks = service.snapshot().scheduledTasks();

    assertThat(tasks).containsOnlyKeys("OgRefreshJob.run", "RequestMetricsRecorder.flush");
    var og = tasks.get("OgRefreshJob.run");
    assertThat(og.count()).isEqualTo(3L);
    assertThat(og.resultCounts()).containsEntry("ok", 2L).containsEntry("error", 1L);
    assertThat(og.maxMillis()).isCloseTo(450.0, within(1.0));
  }

  @Test
  void scheduledTasksEmptyWhenNoneRegistered() {
    assertThat(service.snapshot().scheduledTasks()).isEmpty();
  }
}
