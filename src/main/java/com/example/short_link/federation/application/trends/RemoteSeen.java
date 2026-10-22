package com.example.short_link.federation.application.trends;

import com.example.short_link.federation.domain.SeenUri;
import com.example.short_link.federation.domain.SeenViewer;
import com.example.short_link.federation.domain.repository.RemoteSeenRepository;
import com.example.short_link.federation.domain.repository.RemoteSeenRepository.Sighting;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class RemoteSeen {

  public static final int MAX_PER_REQUEST = 100;
  static final int ROWS_PER_INSERT = 1_000;
  static final int DELETE_BATCH = 5_000;

  private final RemoteSeenRepository repository;
  private final SeenProperties props;
  private final Clock clock;
  private final Counter received;
  private final Counter written;
  private final Counter dropped;
  private final Timer flushTime;
  private final Object lock = new Object();
  private final Object flushing = new Object();

  private Map<SeenViewer, Map<SeenUri, Instant>> pending = new HashMap<>();
  private Map<SeenViewer, Map<SeenUri, Instant>> writing = Map.of();
  private int buffered;

  public RemoteSeen(
      RemoteSeenRepository repository,
      SeenProperties props,
      Clock clock,
      MeterRegistry meterRegistry) {
    this.repository = repository;
    this.props = props;
    this.clock = clock;
    this.received = meterRegistry.counter("federation_seen", "result", "received");
    this.written = meterRegistry.counter("federation_seen", "result", "written");
    this.dropped = meterRegistry.counter("federation_seen", "result", "dropped");
    this.flushTime = meterRegistry.timer("federation_seen_flush");
  }

  public void record(SeenViewer viewer, Collection<String> uris) {
    if (uris == null) {
      return;
    }
    List<SeenUri> valid = new ArrayList<>();
    uris.stream().limit(MAX_PER_REQUEST).forEach(uri -> SeenUri.sent(uri).ifPresent(valid::add));
    if (valid.isEmpty()) {
      return;
    }
    received.increment(valid.size());
    Instant now = clock.instant();
    int over = 0;
    synchronized (lock) {
      Map<SeenUri, Instant> mine = pending.computeIfAbsent(viewer, key -> new HashMap<>());
      for (SeenUri uri : valid) {
        if (mine.containsKey(uri)) {
          continue;
        }
        if (buffered >= props.maxBuffered()) {
          over++;
          continue;
        }
        mine.put(uri, now);
        buffered++;
      }
      if (mine.isEmpty()) {
        pending.remove(viewer);
      }
    }
    if (over > 0) {
      dropped.increment(over);
    }
  }

  public Set<String> seenBefore(SeenViewer viewer, Collection<String> uris, Instant asOf) {
    Map<SeenUri, String> byHash = new LinkedHashMap<>();
    for (String uri : uris) {
      if (uri != null) {
        byHash.putIfAbsent(SeenUri.of(uri), uri);
      }
    }
    if (byHash.isEmpty()) {
      return Set.of();
    }
    Set<SeenUri> seen = new HashSet<>(repository.seenBefore(viewer, byHash.keySet(), asOf));
    synchronized (lock) {
      for (Map<SeenViewer, Map<SeenUri, Instant>> held : List.of(pending, writing)) {
        Map<SeenUri, Instant> mine = held.getOrDefault(viewer, Map.of());
        byHash.keySet().stream()
            .filter(hash -> mine.containsKey(hash) && mine.get(hash).isBefore(asOf))
            .forEach(seen::add);
      }
    }
    Set<String> found = new HashSet<>();
    seen.forEach(hash -> found.add(byHash.get(hash)));
    return found;
  }

  public int flush() {
    synchronized (flushing) {
      Map<SeenViewer, Map<SeenUri, Instant>> batch;
      synchronized (lock) {
        if (pending.isEmpty()) {
          return 0;
        }
        batch = pending;
        writing = batch;
        pending = new HashMap<>();
        buffered = 0;
      }
      try {
        return flushTime.record(() -> write(batch));
      } finally {
        synchronized (lock) {
          writing = Map.of();
        }
      }
    }
  }

  private int write(Map<SeenViewer, Map<SeenUri, Instant>> batch) {
    List<Sighting> rows = new ArrayList<>();
    batch.forEach(
        (viewer, uris) -> uris.forEach((uri, at) -> rows.add(new Sighting(viewer, uri, at))));
    int done = 0;
    for (int from = 0; from < rows.size(); from += ROWS_PER_INSERT) {
      List<Sighting> chunk = rows.subList(from, Math.min(from + ROWS_PER_INSERT, rows.size()));
      try {
        repository.insert(chunk);
        done += chunk.size();
      } catch (RuntimeException failed) {
        dropped.increment(chunk.size());
        log.warn("federation seen write failed rows={} reason={}", chunk.size(), failed.toString());
      }
    }
    written.increment(done);
    return done;
  }

  public int sweep() {
    Instant before = clock.instant().minus(props.keepFor());
    int deleted = 0;
    int batch;
    do {
      batch = repository.deleteBefore(before, DELETE_BATCH);
      deleted += batch;
    } while (batch >= DELETE_BATCH);
    return deleted;
  }

  @PreDestroy
  void flushOnShutdown() {
    flush();
  }
}
