package com.example.short_link.admin.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Component;

/**
 * Per-{@code shortCode} in-memory ring of redirect samples. Each click is recorded as a {@link
 * Sample} carrying timestamp, latency, and outcome — the admin per-link panel reads it back via
 * {@link #sliceWindow(int)}.
 *
 * <p>We deliberately avoid attaching {@code shortCode} as a Micrometer tag — that explodes
 * cardinality on the registry side once a tenant has thousands of links. Instead we keep a single
 * bounded LRU map of {@link Bucket}s keyed by short code and a fixed-size ring of recent samples
 * per bucket. Memory is capped at {@code MAX_TRACKED_LINKS × SAMPLES_PER_LINK × ~40B} which stays
 * comfortably under 5MB. When a new short code arrives after the cap is hit, the least-recently
 * touched bucket is evicted, which mirrors the "top N hot links" behaviour an operator actually
 * cares about.
 *
 * <p>Lifetime totals (which DO need to be exact) come from {@code ClickEventRepository} — this
 * structure is only the source of truth for latency percentiles and outcome breakdown inside the
 * configured windows.
 */
@Component
public class LinkMetricsRecorder {

  /**
   * Hard cap on how many short codes can be tracked at once. Tuned to keep memory bounded even on a
   * runaway test farm (500 × 256 samples × ~40B ≈ 5MB).
   */
  static final int MAX_TRACKED_LINKS = 500;

  /**
   * Per-link rolling sample budget. The ring is overwritten in place once full; latency percentiles
   * are derived from the live snapshot inside whatever window the admin requests.
   */
  static final int SAMPLES_PER_LINK = 256;

  /** Anything older than this is purged on read — no separate sweeper needed. */
  static final int MAX_AGE_MINUTES = 7 * 24 * 60;

  private final Clock clock;
  private final ReentrantLock lock = new ReentrantLock();

  /**
   * Access-ordered LRU so the eviction policy matches "recently active link wins". {@code
   * accessOrder=true} promotes a bucket on every {@code get} so reads-without-writes count as
   * activity too — keeps frequently-inspected links from being evicted during a quiet hour.
   */
  private final LinkedHashMap<String, Bucket> buckets =
      new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Bucket> eldest) {
          return size() > MAX_TRACKED_LINKS;
        }
      };

  public LinkMetricsRecorder() {
    this(Clock.systemUTC());
  }

  LinkMetricsRecorder(Clock clock) {
    this.clock = clock;
  }

  public void record(String shortCode, long latencyMillis, String outcome) {
    if (shortCode == null || shortCode.isBlank()) return;
    String outcomeLabel = outcome == null || outcome.isBlank() ? "other" : outcome;
    lock.lock();
    try {
      Bucket bucket = buckets.computeIfAbsent(shortCode, k -> new Bucket());
      bucket.add(new Sample(clock.instant(), latencyMillis, outcomeLabel));
    } finally {
      lock.unlock();
    }
  }

  /**
   * Returns a snapshot of every tracked short code with samples falling inside the {@code
   * windowMinutes} window. Buckets whose newest sample is older than {@link #MAX_AGE_MINUTES} are
   * dropped from the underlying map as a housekeeping step.
   */
  public Map<String, List<Sample>> sliceWindow(int windowMinutes) {
    Instant now = clock.instant();
    Instant cutoff = now.minusSeconds((long) windowMinutes * 60);
    Instant purgeCutoff = now.minusSeconds((long) MAX_AGE_MINUTES * 60);
    Map<String, List<Sample>> out = new HashMap<>();
    lock.lock();
    try {
      buckets
          .entrySet()
          .removeIf(
              e -> {
                Sample newest = e.getValue().newest();
                return newest == null || newest.timestamp().isBefore(purgeCutoff);
              });
      for (Map.Entry<String, Bucket> entry : buckets.entrySet()) {
        List<Sample> inWindow = entry.getValue().samplesSince(cutoff);
        if (inWindow.isEmpty()) continue;
        out.put(entry.getKey(), inWindow);
      }
    } finally {
      lock.unlock();
    }
    return out;
  }

  /** Test seam — number of distinct short codes currently in memory. */
  int trackedSize() {
    lock.lock();
    try {
      return buckets.size();
    } finally {
      lock.unlock();
    }
  }

  /** Test seam — drop all state between cases. */
  void clear() {
    lock.lock();
    try {
      buckets.clear();
    } finally {
      lock.unlock();
    }
  }

  /** One redirect — kept tiny so the per-link ring stays cheap. */
  public record Sample(Instant timestamp, long latencyMillis, String outcome) {}

  /**
   * Fixed-size ring of {@link Sample}s. The newest sample overwrites the oldest once full; {@link
   * #samplesSince(Instant)} returns whatever is still inside the requested window in
   * newest-first-rebuilt-to-oldest-first order so percentile computation is straightforward.
   */
  static final class Bucket {
    private final Sample[] ring = new Sample[SAMPLES_PER_LINK];
    private int writeIdx = 0;
    private int size = 0;

    void add(Sample sample) {
      ring[writeIdx] = sample;
      writeIdx = (writeIdx + 1) % ring.length;
      if (size < ring.length) size++;
    }

    Sample newest() {
      if (size == 0) return null;
      int idx = (writeIdx - 1 + ring.length) % ring.length;
      return ring[idx];
    }

    /**
     * Returns samples whose timestamp is at or after {@code cutoff}, ordered oldest-first. Walks
     * backwards from the newest slot and stops at the first too-old entry — keeps the common
     * "recent window" case O(window) rather than O(ring).
     */
    List<Sample> samplesSince(Instant cutoff) {
      if (size == 0) return List.of();
      List<Sample> out = new ArrayList<>(Math.min(size, 32));
      for (int i = 0; i < size; i++) {
        int idx = (writeIdx - 1 - i + ring.length) % ring.length;
        Sample s = ring[idx];
        if (s == null || s.timestamp().isBefore(cutoff)) break;
        out.add(s);
      }
      // walk produced newest-first; flip to oldest-first so percentile / chronological math reads
      // naturally
      java.util.Collections.reverse(out);
      return out;
    }
  }

  /**
   * Aggregates a list of {@link Sample}s into the shape the admin UI consumes. Lives on the
   * recorder for unit-testability — kept package-private so the service can call it without
   * exposing internals to the controller.
   */
  static Aggregate aggregate(List<Sample> samples) {
    if (samples.isEmpty()) return Aggregate.empty();
    long count = samples.size();
    long[] latencies = new long[samples.size()];
    Map<String, Long> outcomeCounts = new TreeMap<>();
    Instant lastAt = null;
    for (int i = 0; i < samples.size(); i++) {
      Sample s = samples.get(i);
      latencies[i] = s.latencyMillis();
      outcomeCounts.merge(s.outcome(), 1L, Long::sum);
      if (lastAt == null || s.timestamp().isAfter(lastAt)) lastAt = s.timestamp();
    }
    java.util.Arrays.sort(latencies);
    long p50 = percentile(latencies, 0.50);
    long p95 = percentile(latencies, 0.95);
    long p99 = percentile(latencies, 0.99);
    long errorCount =
        outcomeCounts.getOrDefault("not_found", 0L)
            + outcomeCounts.getOrDefault("expired", 0L)
            + outcomeCounts.getOrDefault("view_limit", 0L)
            + outcomeCounts.getOrDefault("blocked", 0L)
            + outcomeCounts.getOrDefault("error", 0L);
    double errorRate = count == 0 ? 0.0 : (double) errorCount / (double) count;
    // canonicalize the outcome map so the JSON response is stable
    Map<String, Long> sortedOutcomes = new LinkedHashMap<>();
    new TreeMap<>(outcomeCounts).forEach(sortedOutcomes::put);
    return new Aggregate(count, p50, p95, p99, errorRate, sortedOutcomes, lastAt);
  }

  private static long percentile(long[] sorted, double p) {
    if (sorted.length == 0) return 0L;
    if (sorted.length == 1) return sorted[0];
    int idx = (int) Math.ceil(p * sorted.length) - 1;
    if (idx < 0) idx = 0;
    if (idx >= sorted.length) idx = sorted.length - 1;
    return sorted[idx];
  }

  /** Pre-aggregated view used by {@link AdminLinkMetricsService}. */
  record Aggregate(
      long count,
      long p50Millis,
      long p95Millis,
      long p99Millis,
      double errorRate,
      Map<String, Long> outcomeCounts,
      Instant lastAt) {

    static Aggregate empty() {
      return new Aggregate(0, 0, 0, 0, 0.0, Map.of(), null);
    }
  }

  /**
   * Test/debug helper — produces the same ordering the admin UI uses (count desc) over the full
   * tracked set.
   */
  List<Map.Entry<String, Aggregate>> snapshot(int windowMinutes) {
    List<Map.Entry<String, Aggregate>> out = new ArrayList<>();
    sliceWindow(windowMinutes)
        .forEach((code, samples) -> out.add(Map.entry(code, aggregate(samples))));
    out.sort(
        Comparator.comparingLong((Map.Entry<String, Aggregate> e) -> e.getValue().count())
            .reversed());
    return out;
  }
}
