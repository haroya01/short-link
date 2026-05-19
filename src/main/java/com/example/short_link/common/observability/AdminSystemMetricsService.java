package com.example.short_link.common.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.search.Search;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

/**
 * Surfaces what Micrometer / Actuator already collects ({@code jvm.*}, {@code hikaricp.*}, {@code
 * cache.*}) as a single admin endpoint payload. Zero code-path overhead — these gauges are computed
 * lazily on read and the admin call hits them only when the operator opens the dashboard.
 *
 * <p>Existence of each gauge depends on autoconfig — if the cache implementation doesn't ship
 * Micrometer bindings (Redis cache without Lettuce metrics, for example), the corresponding field
 * reads as {@code null} / 0 rather than throwing.
 */
@Service
public class AdminSystemMetricsService {

  private final MeterRegistry registry;

  public AdminSystemMetricsService(MeterRegistry registry) {
    this.registry = registry;
  }

  public SystemMetrics snapshot() {
    return new SystemMetrics(jvm(), hikari(), caches(), outboundHttp(), scheduledTasks());
  }

  /**
   * Per-{@code task} scheduled job timer aggregates. Same mean/max-only shape as {@link
   * #outboundHttp()} for the same reason (no histogram autoconfig). Result counts split ok vs error
   * so a dashboard can flag a job that's quietly throwing every fire without anyone noticing.
   */
  private Map<String, ScheduledTaskStat> scheduledTasks() {
    Map<String, Map<String, Long>> resultCountsByTask = new LinkedHashMap<>();
    Map<String, Long> totalCountByTask = new LinkedHashMap<>();
    Map<String, Double> totalMsByTask = new LinkedHashMap<>();
    Map<String, Double> maxMsByTask = new LinkedHashMap<>();
    Search taskSearch = registry.find("scheduled.task");
    for (Timer t : taskSearch.timers()) {
      String task = t.getId().getTag("task");
      String result = t.getId().getTag("result");
      if (task == null) continue;
      long c = t.count();
      totalCountByTask.merge(task, c, Long::sum);
      totalMsByTask.merge(task, t.totalTime(TimeUnit.MILLISECONDS), Double::sum);
      maxMsByTask.merge(task, t.max(TimeUnit.MILLISECONDS), Double::max);
      if (result != null) {
        resultCountsByTask
            .computeIfAbsent(task, k -> new LinkedHashMap<>())
            .merge(result, c, Long::sum);
      }
    }
    Map<String, ScheduledTaskStat> out = new LinkedHashMap<>();
    for (String task : totalCountByTask.keySet()) {
      long count = totalCountByTask.get(task);
      double total = totalMsByTask.getOrDefault(task, 0.0);
      double max = maxMsByTask.getOrDefault(task, 0.0);
      double mean = count == 0 ? 0.0 : total / count;
      out.put(
          task,
          new ScheduledTaskStat(count, mean, max, resultCountsByTask.getOrDefault(task, Map.of())));
    }
    return out;
  }

  /**
   * Per-{@code client} outbound HTTP timer aggregates ({@code webhook}, {@code og_fetch}). Mean and
   * max are sourced directly from the timer; we don't request percentile because the default Spring
   * autoconfig doesn't enable histogram publication on these timers (would inflate registry size
   * with no operator-side benefit at 100k req/day). The dashboard reads mean / max with a "look at
   * request_metrics for per-call drill-down" hint instead.
   */
  private Map<String, OutboundHttpStat> outboundHttp() {
    Map<String, Map<String, Long>> resultCountsByClient = new LinkedHashMap<>();
    Map<String, Long> totalCountByClient = new LinkedHashMap<>();
    Map<String, Double> totalMsByClient = new LinkedHashMap<>();
    Map<String, Double> maxMsByClient = new LinkedHashMap<>();
    Search outboundSearch = registry.find("outbound.http");
    for (Timer t : outboundSearch.timers()) {
      String client = t.getId().getTag("client");
      String result = t.getId().getTag("result");
      if (client == null) continue;
      long c = t.count();
      totalCountByClient.merge(client, c, Long::sum);
      totalMsByClient.merge(client, t.totalTime(TimeUnit.MILLISECONDS), Double::sum);
      maxMsByClient.merge(client, t.max(TimeUnit.MILLISECONDS), Double::max);
      if (result != null) {
        resultCountsByClient
            .computeIfAbsent(client, k -> new LinkedHashMap<>())
            .merge(result, c, Long::sum);
      }
    }
    Map<String, OutboundHttpStat> out = new LinkedHashMap<>();
    for (String client : totalCountByClient.keySet()) {
      long count = totalCountByClient.get(client);
      double total = totalMsByClient.getOrDefault(client, 0.0);
      double max = maxMsByClient.getOrDefault(client, 0.0);
      double mean = count == 0 ? 0.0 : total / count;
      out.put(
          client,
          new OutboundHttpStat(
              count, mean, max, resultCountsByClient.getOrDefault(client, Map.of())));
    }
    return out;
  }

  private JvmStats jvm() {
    long heapUsed = (long) gauge("jvm.memory.used", "area", "heap");
    long heapMax = (long) gauge("jvm.memory.max", "area", "heap");
    long nonHeapUsed = (long) gauge("jvm.memory.used", "area", "nonheap");
    int threadsLive = (int) gauge("jvm.threads.live");
    int threadsDaemon = (int) gauge("jvm.threads.daemon");
    GcStat gc = gcStat();
    return new JvmStats(heapUsed, heapMax, nonHeapUsed, threadsLive, threadsDaemon, gc);
  }

  private HikariStats hikari() {
    int active = (int) gauge("hikaricp.connections.active");
    int idle = (int) gauge("hikaricp.connections.idle");
    int pending = (int) gauge("hikaricp.connections.pending");
    int total = (int) gauge("hikaricp.connections");
    double acquireMillisMean = timerMean("hikaricp.connections.acquire");
    return new HikariStats(active, idle, pending, total, acquireMillisMean);
  }

  /**
   * Per-{@code @Cacheable} cache, sums {@code cache.gets} grouped by {@code result=hit|miss}. The
   * read happens against whatever caches are currently registered — Redis / Caffeine / etc. all
   * expose the same metric shape via Micrometer's {@code CacheMeterBinder} contract.
   */
  private Map<String, CacheStat> caches() {
    Map<String, CacheStat> out = new LinkedHashMap<>();
    Search getsSearch = registry.find("cache.gets");
    for (var meter : getsSearch.meters()) {
      String name = meter.getId().getTag("cache");
      if (name == null) continue;
      out.computeIfAbsent(name, k -> new CacheStat(0L, 0L));
    }
    for (String name : out.keySet()) {
      long hits = counterSum("cache.gets", "cache", name, "result", "hit");
      long misses = counterSum("cache.gets", "cache", name, "result", "miss");
      out.put(name, new CacheStat(hits, misses));
    }
    return out;
  }

  private double gauge(String name, String... tagPairs) {
    Search s = registry.find(name);
    for (int i = 0; i + 1 < tagPairs.length; i += 2) {
      s = s.tag(tagPairs[i], tagPairs[i + 1]);
    }
    return s.gauges().stream().mapToDouble(g -> g.value()).sum();
  }

  private long counterSum(String name, String... tagPairs) {
    Search s = registry.find(name);
    for (int i = 0; i + 1 < tagPairs.length; i += 2) {
      s = s.tag(tagPairs[i], tagPairs[i + 1]);
    }
    return s.counters().stream().mapToLong(c -> (long) c.count()).sum();
  }

  private double timerMean(String name) {
    Search s = registry.find(name);
    var timers = s.timers();
    if (timers.isEmpty()) return 0.0;
    double totalMs = timers.stream().mapToDouble(t -> t.totalTime(TimeUnit.MILLISECONDS)).sum();
    long count = timers.stream().mapToLong(Timer::count).sum();
    return count == 0 ? 0.0 : totalMs / count;
  }

  private GcStat gcStat() {
    Search s = registry.find("jvm.gc.pause");
    var timers = s.timers();
    if (timers.isEmpty()) return new GcStat(0L, 0.0, 0.0);
    long count = timers.stream().mapToLong(Timer::count).sum();
    double totalMs = timers.stream().mapToDouble(t -> t.totalTime(TimeUnit.MILLISECONDS)).sum();
    double maxMs = timers.stream().mapToDouble(t -> t.max(TimeUnit.MILLISECONDS)).max().orElse(0.0);
    double meanMs = count == 0 ? 0.0 : totalMs / count;
    return new GcStat(count, meanMs, maxMs);
  }

  public record SystemMetrics(
      JvmStats jvm,
      HikariStats hikari,
      Map<String, CacheStat> caches,
      Map<String, OutboundHttpStat> outboundHttp,
      Map<String, ScheduledTaskStat> scheduledTasks) {}

  public record OutboundHttpStat(
      long count, double meanMillis, double maxMillis, Map<String, Long> resultCounts) {}

  public record ScheduledTaskStat(
      long count, double meanMillis, double maxMillis, Map<String, Long> resultCounts) {}

  public record JvmStats(
      long heapUsedBytes,
      long heapMaxBytes,
      long nonHeapUsedBytes,
      int threadsLive,
      int threadsDaemon,
      GcStat gc) {}

  public record GcStat(long count, double meanMillis, double maxMillis) {}

  public record HikariStats(
      int active, int idle, int pending, int total, double acquireMillisMean) {}

  public record CacheStat(long hits, long misses) {

    public double hitRatio() {
      long total = hits + misses;
      return total == 0 ? 0.0 : (double) hits / (double) total;
    }
  }
}
