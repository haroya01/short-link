package com.example.short_link.admin.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.admin.application.RouteMetricsRingBuffer.MinuteBucket;
import com.example.short_link.admin.application.RouteMetricsRingBuffer.RouteKey;
import com.example.short_link.admin.application.RouteMetricsRingBuffer.TupleKey;
import com.example.short_link.admin.application.RouteMetricsRingBuffer.TupleSlice;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RouteMetricsRingBufferTest {

  @Test
  void firstSampleEstablishesBaselineButProducesNoBucket() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    registry
        .timer("http.server.requests", Tags.of("method", "GET", "uri", "/x", "status", "200"))
        .record(java.time.Duration.ofMillis(10));
    Clock clock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneId.of("UTC"));
    RouteMetricsRingBuffer buf = new RouteMetricsRingBuffer(registry, clock);
    buf.sample();
    Map<RouteKey, List<TupleSlice>> out = buf.sliceWindow(60);
    assertThat(out).isEmpty();
  }

  @Test
  void secondSampleProducesDeltaBucket() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    io.micrometer.core.instrument.Timer t =
        registry.timer(
            "http.server.requests", Tags.of("method", "GET", "uri", "/x", "status", "200"));
    t.record(java.time.Duration.ofMillis(10));
    Clock clock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneId.of("UTC"));
    RouteMetricsRingBuffer buf = new RouteMetricsRingBuffer(registry, clock);
    buf.sample();
    t.record(java.time.Duration.ofMillis(20));
    t.record(java.time.Duration.ofMillis(30));
    buf.sample();
    Map<RouteKey, List<TupleSlice>> out = buf.sliceWindow(60);
    assertThat(out).hasSize(1);
    RouteKey k = out.keySet().iterator().next();
    assertThat(k.method()).isEqualTo("GET");
    assertThat(k.uri()).isEqualTo("/x");
    List<TupleSlice> slices = out.get(k);
    assertThat(slices).hasSize(1);
    assertThat(slices.get(0).status()).isEqualTo("200");
    assertThat(slices.get(0).buckets()).hasSize(1);
    MinuteBucket b = slices.get(0).buckets().get(0);
    assertThat(b.count()).isEqualTo(2L);
  }

  @Test
  void sliceWindowFiltersBucketsByCutoff() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    io.micrometer.core.instrument.Timer t =
        registry.timer(
            "http.server.requests", Tags.of("method", "GET", "uri", "/x", "status", "200"));
    t.record(java.time.Duration.ofMillis(10));
    java.util.concurrent.atomic.AtomicReference<Instant> ref =
        new java.util.concurrent.atomic.AtomicReference<>(Instant.parse("2024-01-01T00:00:00Z"));
    Clock clock =
        new Clock() {
          @Override
          public Instant instant() {
            return ref.get();
          }

          @Override
          public ZoneId getZone() {
            return ZoneId.of("UTC");
          }

          @Override
          public Clock withZone(ZoneId zone) {
            return this;
          }
        };
    RouteMetricsRingBuffer buf = new RouteMetricsRingBuffer(registry, clock);
    buf.sample();
    ref.set(Instant.parse("2024-01-01T00:01:00Z"));
    t.record(java.time.Duration.ofMillis(20));
    buf.sample();
    // Move clock far beyond requested window — bucket should fall outside.
    ref.set(Instant.parse("2024-01-02T01:00:00Z"));
    Map<RouteKey, List<TupleSlice>> out = buf.sliceWindow(10);
    assertThat(out).isEmpty();
  }

  @Test
  void recordsExposeAccessors() {
    TupleKey tk = new TupleKey("GET", "/x", "200");
    assertThat(tk.method()).isEqualTo("GET");
    assertThat(tk.uri()).isEqualTo("/x");
    assertThat(tk.status()).isEqualTo("200");
    RouteKey rk = new RouteKey("GET", "/x");
    assertThat(rk.method()).isEqualTo("GET");
    MinuteBucket b = new MinuteBucket(Instant.EPOCH, 1L, 1.0, 2.0);
    assertThat(b.count()).isEqualTo(1L);
    assertThat(b.p95Millis()).isEqualTo(1.0);
    assertThat(b.p99Millis()).isEqualTo(2.0);
    TupleSlice s = new TupleSlice("200", List.of(b));
    assertThat(s.status()).isEqualTo("200");
    assertThat(s.buckets()).hasSize(1);
  }
}
