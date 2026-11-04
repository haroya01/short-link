package com.example.short_link.live.application;

import com.example.short_link.common.live.LiveChange;
import com.example.short_link.common.live.LiveRevoke;
import com.example.short_link.common.live.LiveScope;
import com.example.short_link.common.live.LiveTopic;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LiveStreams implements SmartLifecycle {

  static final int MAX_ITEMS = 20;
  static final int RETRY_AFTER_SECONDS = 60;

  private static final LiveEvent HEARTBEAT = new LiveEvent.Heartbeat();

  private final LiveProperties props;
  private final Clock clock;
  private final MeterRegistry meters;
  private final ScheduledThreadPoolExecutor worker;
  private final Map<LiveTopic, Set<LiveSubscriber>> watchers = new ConcurrentHashMap<>();
  private final Map<Long, Set<LiveSubscriber>> byUser = new HashMap<>();
  private final Map<LiveTopic, Batch> pending = new ConcurrentHashMap<>();
  private final ReentrantLock admission = new ReentrantLock();
  private final AtomicInteger open = new AtomicInteger();
  private volatile boolean running;
  private ScheduledFuture<?> heartbeat;

  public LiveStreams(LiveProperties props, Clock clock, MeterRegistry meters) {
    this.props = props;
    this.clock = clock;
    this.meters = meters;
    this.worker =
        new ScheduledThreadPoolExecutor(
            1, work -> Thread.ofPlatform().name("kurl-live").daemon().unstarted(work));
    this.worker.setRemoveOnCancelPolicy(true);
    Gauge.builder("live.streams.open", open, AtomicInteger::get).register(meters);
  }

  public boolean admits(long userId) {
    admission.lock();
    try {
      return open.get() < props.maxStreams() || countOf(userId) >= props.maxStreamsPerUser();
    } finally {
      admission.unlock();
    }
  }

  public boolean register(LiveSubscriber subscriber) {
    LiveSubscriber oldest = null;
    admission.lock();
    try {
      Set<LiveSubscriber> mine = byUser.get(subscriber.userId());
      if (mine != null && mine.size() >= props.maxStreamsPerUser()) {
        oldest = mine.iterator().next();
        unlist(oldest);
      } else if (open.get() >= props.maxStreams()) {
        meters.counter("live.streams.rejected").increment();
        return false;
      }
      byUser.computeIfAbsent(subscriber.userId(), user -> new LinkedHashSet<>()).add(subscriber);
      watchers
          .computeIfAbsent(subscriber.topic(), topic -> ConcurrentHashMap.newKeySet())
          .add(subscriber);
      open.incrementAndGet();
    } finally {
      admission.unlock();
    }
    if (oldest != null) {
      LiveSubscriber replaced = oldest;
      later(() -> crowdOut(replaced), Duration.ZERO);
    }
    return true;
  }

  public void remove(LiveSubscriber subscriber) {
    admission.lock();
    try {
      unlist(subscriber);
    } finally {
      admission.unlock();
    }
  }

  private void unlist(LiveSubscriber subscriber) {
    Set<LiveSubscriber> mine = byUser.get(subscriber.userId());
    if (mine == null || !mine.remove(subscriber)) {
      return;
    }
    if (mine.isEmpty()) {
      byUser.remove(subscriber.userId());
    }
    Set<LiveSubscriber> watching = watchers.get(subscriber.topic());
    if (watching != null) {
      watching.remove(subscriber);
      if (watching.isEmpty()) {
        watchers.remove(subscriber.topic());
      }
    }
    open.decrementAndGet();
  }

  private void crowdOut(LiveSubscriber subscriber) {
    synchronized (subscriber) {
      deliver(subscriber, new LiveEvent.Busy(RETRY_AFTER_SECONDS));
    }
    subscriber.channel().close();
    meters.counter("live.streams.crowded-out").increment();
  }

  public void start(LiveSubscriber subscriber, long latest, Long since) {
    synchronized (subscriber) {
      subscriber.advance(latest);
      LiveTopic topic = subscriber.topic();
      if (!deliver(
          subscriber,
          new LiveEvent.Ready(
              topic.scope().apiName(), topic.id(), String.valueOf(subscriber.cursor())))) {
        return;
      }
      subscriber.markReady();
      if (subscriber.missed() || (since != null && subscriber.cursor() > since)) {
        deliver(subscriber, changed(subscriber, List.of(), true, clock.instant()));
      }
    }
  }

  public void offer(LiveChange change) {
    Set<LiveSubscriber> watching = watchers.get(change.topic());
    if (change.direct() || watching == null || watching.isEmpty()) {
      return;
    }
    boolean[] opened = {false};
    pending.compute(
        change.topic(),
        (topic, batch) -> {
          Batch next = batch == null ? new Batch() : batch;
          opened[0] = batch == null;
          next.add(change);
          return next;
        });
    if (opened[0]) {
      later(() -> flush(change.topic()), props.windowFor(open.get()));
    }
  }

  public void revoke(LiveRevoke revoke) {
    if (open.get() == 0) {
      return;
    }
    later(() -> end(revoke), Duration.ZERO);
  }

  int open() {
    return open.get();
  }

  void flush(LiveTopic topic) {
    Batch batch = pending.remove(topic);
    Set<LiveSubscriber> watching = watchers.get(topic);
    if (batch == null || watching == null) {
      return;
    }
    Instant at = clock.instant();
    for (LiveSubscriber subscriber : watching) {
      List<LiveEvent.Item> heard = batch.heardFrom(subscriber.scope());
      synchronized (subscriber) {
        subscriber.advance(batch.latest);
        if (!subscriber.ready()) {
          subscriber.miss();
          continue;
        }
        if (heard.isEmpty() && !batch.cut) {
          continue;
        }
        if (deliver(subscriber, changed(subscriber, heard, batch.cut, at))) {
          meters.counter("live.changes.delivered").increment();
        }
      }
    }
  }

  void beat() {
    for (Set<LiveSubscriber> watching : watchers.values()) {
      for (LiveSubscriber subscriber : watching) {
        synchronized (subscriber) {
          if (subscriber.ready()) {
            deliver(subscriber, HEARTBEAT);
          }
        }
      }
    }
  }

  private void end(LiveRevoke revoke) {
    List<LiveSubscriber> ended = new ArrayList<>();
    for (Set<LiveSubscriber> watching : watchers.values()) {
      for (LiveSubscriber subscriber : watching) {
        if (revoke.ends(subscriber.userId(), subscriber.topic(), subscriber.scope())) {
          ended.add(subscriber);
        }
      }
    }
    boolean told = !(revoke instanceof LiveRevoke.Recheck);
    for (LiveSubscriber subscriber : ended) {
      if (told) {
        synchronized (subscriber) {
          LiveTopic topic = subscriber.topic();
          deliver(subscriber, new LiveEvent.Revoked(topic.scope().apiName(), topic.id()));
        }
      }
      drop(subscriber);
      meters.counter("live.streams.ended", "revoked", String.valueOf(told)).increment();
    }
  }

  private LiveEvent changed(
      LiveSubscriber subscriber, List<LiveEvent.Item> items, boolean more, Instant at) {
    LiveTopic topic = subscriber.topic();
    return new LiveEvent.Changed(
        topic.scope().apiName(),
        topic.id(),
        items,
        more,
        String.valueOf(subscriber.cursor()),
        at.toString());
  }

  private boolean deliver(LiveSubscriber subscriber, LiveEvent event) {
    try {
      subscriber.channel().send(event);
      return true;
    } catch (IOException | IllegalStateException e) {
      drop(subscriber);
      return false;
    }
  }

  private void drop(LiveSubscriber subscriber) {
    subscriber.channel().close();
    remove(subscriber);
  }

  private int countOf(long userId) {
    Set<LiveSubscriber> mine = byUser.get(userId);
    return mine == null ? 0 : mine.size();
  }

  private void later(Runnable work, Duration delay) {
    try {
      worker.schedule(guarded(work), delay.toMillis(), TimeUnit.MILLISECONDS);
    } catch (RejectedExecutionException stopping) {
      log.debug("live worker stopped; dropped a delivery");
    }
  }

  private static Runnable guarded(Runnable work) {
    return () -> {
      try {
        work.run();
      } catch (RuntimeException e) {
        log.warn("live delivery failed", e);
      }
    };
  }

  @Override
  public void start() {
    long every = props.heartbeat().toMillis();
    heartbeat =
        worker.scheduleAtFixedRate(guarded(this::beat), every, every, TimeUnit.MILLISECONDS);
    running = true;
  }

  @Override
  public void stop() {
    running = false;
    if (heartbeat != null) {
      heartbeat.cancel(false);
    }
    List<LiveSubscriber> all = new ArrayList<>();
    admission.lock();
    try {
      byUser.values().forEach(all::addAll);
    } finally {
      admission.unlock();
    }
    all.forEach(this::drop);
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  @PreDestroy
  void shutdown() {
    stop();
    worker.shutdownNow();
  }

  private static final class Batch {

    private final List<LiveChange> changes = new ArrayList<>();
    private boolean cut;
    private long latest;

    void add(LiveChange change) {
      latest = Math.max(latest, change.itemId());
      if (changes.size() < MAX_ITEMS) {
        changes.add(change);
      } else {
        cut = true;
      }
    }

    List<LiveEvent.Item> heardFrom(LiveScope scope) {
      return changes.stream()
          .filter(change -> change.roomId() == null || change.roomId().equals(scope.roomId()))
          .map(change -> new LiveEvent.Item(change.kind().apiName(), change.itemId()))
          .toList();
    }
  }
}
