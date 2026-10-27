package com.example.short_link.federation.application.trends;

import com.example.short_link.common.seen.SeenItem;
import com.example.short_link.common.seen.SeenViewer;
import com.example.short_link.common.seen.Unseen;
import com.example.short_link.federation.domain.repository.LocalSeenRepository;
import com.example.short_link.federation.domain.repository.LocalSeenRepository.Sighting;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class LocalSeen {

  static final int ROWS_PER_INSERT = 1_000;
  static final int DELETE_BATCH = 5_000;
  static final int MAX_HELD = 1_000;

  private final LocalSeenRepository repository;
  private final SeenProperties props;
  private final Clock clock;
  private final Counter received;
  private final Counter written;
  private final Counter dropped;
  private final Timer flushTime;
  private final Object lock = new Object();
  private final Object flushing = new Object();

  private Map<SeenViewer, Map<SeenItem, Instant>> pending = new HashMap<>();
  private Map<SeenViewer, Map<SeenItem, Instant>> writing = Map.of();
  private int buffered;

  public LocalSeen(
      LocalSeenRepository repository,
      SeenProperties props,
      Clock clock,
      MeterRegistry meterRegistry) {
    this.repository = repository;
    this.props = props;
    this.clock = clock;
    this.received = meterRegistry.counter("home_seen", "result", "received");
    this.written = meterRegistry.counter("home_seen", "result", "written");
    this.dropped = meterRegistry.counter("home_seen", "result", "dropped");
    this.flushTime = meterRegistry.timer("home_seen_flush");
  }

  public int record(SeenViewer viewer, Collection<String> keys) {
    if (keys == null) {
      return 0;
    }
    List<SeenItem> valid = new ArrayList<>();
    keys.stream()
        .limit(RemoteSeen.MAX_PER_REQUEST)
        .forEach(key -> SeenItem.sent(key).ifPresent(valid::add));
    if (valid.isEmpty()) {
      return 0;
    }
    received.increment(valid.size());
    Instant now = clock.instant();
    int over = 0;
    synchronized (lock) {
      Map<SeenItem, Instant> mine = pending.computeIfAbsent(viewer, key -> new HashMap<>());
      for (SeenItem item : valid) {
        if (mine.containsKey(item)) {
          continue;
        }
        if (buffered >= props.maxBuffered()) {
          over++;
          continue;
        }
        mine.put(item, now);
        buffered++;
      }
      if (mine.isEmpty()) {
        pending.remove(viewer);
      }
    }
    if (over > 0) {
      dropped.increment(over);
    }
    return valid.size();
  }

  public Unseen unseen(SeenViewer viewer, Instant asOf) {
    List<Long> notes = new ArrayList<>();
    List<Long> posts = new ArrayList<>();
    synchronized (lock) {
      for (Map<SeenViewer, Map<SeenItem, Instant>> held : List.of(pending, writing)) {
        held.getOrDefault(viewer, Map.of())
            .forEach(
                (item, at) -> {
                  List<Long> into = item.kind() == SeenItem.NOTE ? notes : posts;
                  if (at.isBefore(asOf) && into.size() < MAX_HELD) {
                    into.add(item.id());
                  }
                });
      }
    }
    return new Unseen(viewer.kind(), viewer.key(), asOf, notes, posts);
  }

  public Set<SeenItem> seenBefore(SeenViewer viewer, Collection<SeenItem> items, Instant asOf) {
    if (items.isEmpty()) {
      return Set.of();
    }
    Set<SeenItem> seen = new HashSet<>(repository.seenBefore(viewer, Set.copyOf(items), asOf));
    synchronized (lock) {
      for (Map<SeenViewer, Map<SeenItem, Instant>> held : List.of(pending, writing)) {
        Map<SeenItem, Instant> mine = held.getOrDefault(viewer, Map.of());
        items.stream()
            .filter(item -> mine.containsKey(item) && mine.get(item).isBefore(asOf))
            .forEach(seen::add);
      }
    }
    return seen;
  }

  public int flush() {
    synchronized (flushing) {
      Map<SeenViewer, Map<SeenItem, Instant>> batch;
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

  private int write(Map<SeenViewer, Map<SeenItem, Instant>> batch) {
    List<Sighting> rows = new ArrayList<>();
    batch.forEach(
        (viewer, items) -> items.forEach((item, at) -> rows.add(new Sighting(viewer, item, at))));
    int done = 0;
    for (int from = 0; from < rows.size(); from += ROWS_PER_INSERT) {
      List<Sighting> chunk = rows.subList(from, Math.min(from + ROWS_PER_INSERT, rows.size()));
      try {
        repository.insert(chunk);
        done += chunk.size();
      } catch (RuntimeException failed) {
        dropped.increment(chunk.size());
        log.warn("home seen write failed rows={} reason={}", chunk.size(), failed.toString());
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
