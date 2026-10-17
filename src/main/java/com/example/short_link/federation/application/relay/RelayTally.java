package com.example.short_link.federation.application.relay;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

// What became of each announce, today (Seoul) for the admin view and since start as the
// federation.relay counter. The day's counts live in process memory only.
@Component
public class RelayTally {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  private final MeterRegistry meters;
  private final Clock clock;
  private final Map<String, LongAdder> today = new ConcurrentHashMap<>();
  private volatile LocalDate day;

  @Autowired
  public RelayTally(MeterRegistry meters) {
    this(meters, Clock.systemUTC());
  }

  RelayTally(MeterRegistry meters, Clock clock) {
    this.meters = meters;
    this.clock = clock;
    this.day = LocalDate.now(clock.withZone(SEOUL));
  }

  public void count(String outcome) {
    count(outcome, 1);
  }

  public void count(String outcome, long times) {
    meters.counter("federation.relay", "outcome", outcome).increment(times);
    seed(outcome, times);
  }

  public void seed(String outcome, long times) {
    roll();
    today.computeIfAbsent(outcome, key -> new LongAdder()).add(times);
  }

  public Instant dayStart() {
    return LocalDate.now(clock.withZone(SEOUL)).atStartOfDay(SEOUL).toInstant();
  }

  public long today(String outcome) {
    roll();
    LongAdder counted = today.get(outcome);
    return counted == null ? 0 : counted.sum();
  }

  public Map<String, Long> today() {
    roll();
    Map<String, Long> counts = new TreeMap<>();
    today.forEach((outcome, counted) -> counts.put(outcome, counted.sum()));
    return counts;
  }

  private void roll() {
    LocalDate now = LocalDate.now(clock.withZone(SEOUL));
    if (!now.equals(day)) {
      synchronized (this) {
        if (!now.equals(day)) {
          today.clear();
          day = now;
        }
      }
    }
  }
}
