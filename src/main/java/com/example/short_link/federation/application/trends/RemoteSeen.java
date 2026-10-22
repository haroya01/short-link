package com.example.short_link.federation.application.trends;

import com.example.short_link.federation.domain.SeenUri;
import com.example.short_link.federation.domain.SeenViewer;
import com.example.short_link.federation.domain.repository.RemoteDwellRepository;
import com.example.short_link.federation.domain.repository.RemoteNoteReachRepository;
import com.example.short_link.federation.domain.repository.RemoteSeenRepository;
import com.example.short_link.federation.domain.repository.RemoteSeenRepository.Sighting;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
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
  public static final int MIN_DWELL_MS = 1_000;
  public static final int MAX_DWELL_MS = 600_000;
  public static final Set<String> FEEDS = Set.of("for-you", "latest", "trending");
  static final int ROWS_PER_INSERT = 1_000;
  static final int DELETE_BATCH = 5_000;
  static final Duration KEEP_DWELL = Duration.ofDays(30);

  public record Dwell(String uri, long ms) {}

  private final RemoteSeenRepository repository;
  private final RemoteDwellRepository dwells;
  private final RemoteNoteReachRepository notes;
  private final SeenProperties props;
  private final Clock clock;
  private final Counter received;
  private final Counter written;
  private final Counter dropped;
  private final Timer flushTime;
  private final MeterRegistry meters;
  private final Object lock = new Object();
  private final Object flushing = new Object();

  private Map<SeenViewer, Map<SeenUri, Instant>> pending = new HashMap<>();
  private Map<SeenViewer, Map<SeenUri, Instant>> writing = Map.of();
  private int buffered;
  private Map<SeenViewer, Map<String, Integer>> dwelling = new HashMap<>();
  private int dwellBuffered;

  public RemoteSeen(
      RemoteSeenRepository repository,
      RemoteDwellRepository dwells,
      RemoteNoteReachRepository notes,
      SeenProperties props,
      Clock clock,
      MeterRegistry meterRegistry) {
    this.repository = repository;
    this.dwells = dwells;
    this.notes = notes;
    this.props = props;
    this.meters = meterRegistry;
    this.clock = clock;
    this.received = meterRegistry.counter("federation_seen", "result", "received");
    this.written = meterRegistry.counter("federation_seen", "result", "written");
    this.dropped = meterRegistry.counter("federation_seen", "result", "dropped");
    this.flushTime = meterRegistry.timer("federation_seen_flush");
  }

  // A feed outside FEEDS is not counted; a dwell under a second is dropped and one over ten minutes
  // is cut to ten.
  public void record(
      SeenViewer viewer, Collection<String> uris, Collection<Dwell> dwelt, String feed) {
    int shown = record(viewer, uris);
    boolean known = feed != null && FEEDS.contains(feed);
    if (known && shown > 0) {
      meters.counter("federation_seen_feed", "feed", feed).increment(shown);
    }
    if (dwelt == null) {
      return;
    }
    Map<String, Integer> valid = new HashMap<>();
    dwelt.stream()
        .limit(MAX_PER_REQUEST)
        .filter(dwell -> dwell != null && dwell.ms() >= MIN_DWELL_MS)
        .filter(dwell -> SeenUri.sent(dwell.uri()).isPresent())
        .forEach(
            dwell -> valid.merge(dwell.uri(), (int) Math.min(dwell.ms(), MAX_DWELL_MS), Math::max));
    if (valid.isEmpty()) {
      return;
    }
    valid
        .values()
        .forEach(
            ms -> meters.summary("federation_dwell", "feed", known ? feed : "unknown").record(ms));
    int over = 0;
    synchronized (lock) {
      Map<String, Integer> mine = dwelling.computeIfAbsent(viewer, key -> new HashMap<>());
      for (Map.Entry<String, Integer> dwell : valid.entrySet()) {
        if (!mine.containsKey(dwell.getKey())) {
          if (dwellBuffered >= props.maxBuffered()) {
            over++;
            continue;
          }
          dwellBuffered++;
        }
        mine.merge(dwell.getKey(), dwell.getValue(), Math::max);
      }
      if (mine.isEmpty()) {
        dwelling.remove(viewer);
      }
    }
    if (over > 0) {
      meters.counter("federation_dwell_rows", "result", "dropped").increment(over);
    }
  }

  public int record(SeenViewer viewer, Collection<String> uris) {
    if (uris == null) {
      return 0;
    }
    List<SeenUri> valid = new ArrayList<>();
    uris.stream().limit(MAX_PER_REQUEST).forEach(uri -> SeenUri.sent(uri).ifPresent(valid::add));
    if (valid.isEmpty()) {
      return 0;
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
    return valid.size();
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
      flushDwell();
      return flushSeen();
    }
  }

  private void flushDwell() {
    Map<SeenViewer, Map<String, Integer>> batch;
    synchronized (lock) {
      if (dwelling.isEmpty()) {
        return;
      }
      batch = dwelling;
      dwelling = new HashMap<>();
      dwellBuffered = 0;
    }
    Set<String> uris = new HashSet<>();
    batch.values().forEach(sent -> uris.addAll(sent.keySet()));
    Instant now = clock.instant();
    List<RemoteDwellRepository.Dwell> rows = new ArrayList<>();
    int unknown = 0;
    try {
      Map<String, Long> ids = new HashMap<>();
      List<String> asked = List.copyOf(uris);
      for (int from = 0; from < asked.size(); from += ROWS_PER_INSERT) {
        ids.putAll(
            notes.idsByUri(asked.subList(from, Math.min(from + ROWS_PER_INSERT, asked.size()))));
      }
      for (Map.Entry<SeenViewer, Map<String, Integer>> viewer : batch.entrySet()) {
        for (Map.Entry<String, Integer> dwell : viewer.getValue().entrySet()) {
          Long noteId = ids.get(dwell.getKey());
          if (noteId == null) {
            unknown++;
          } else {
            rows.add(
                new RemoteDwellRepository.Dwell(viewer.getKey(), noteId, dwell.getValue(), now));
          }
        }
      }
      for (int from = 0; from < rows.size(); from += ROWS_PER_INSERT) {
        dwells.record(rows.subList(from, Math.min(from + ROWS_PER_INSERT, rows.size())));
      }
      meters.counter("federation_dwell_rows", "result", "written").increment(rows.size());
      meters.counter("federation_dwell_rows", "result", "unknown").increment(unknown);
    } catch (RuntimeException failed) {
      meters.counter("federation_dwell_rows", "result", "dropped").increment(rows.size());
      log.warn("federation dwell write failed reason={}", failed.toString());
    }
  }

  private int flushSeen() {
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
    Instant dwellBefore = clock.instant().minus(KEEP_DWELL);
    do {
      batch = dwells.deleteBefore(dwellBefore, DELETE_BATCH);
      deleted += batch;
    } while (batch >= DELETE_BATCH);
    return deleted;
  }

  @PreDestroy
  void flushOnShutdown() {
    flush();
  }
}
