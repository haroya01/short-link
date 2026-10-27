package com.example.short_link.federation.application.trends;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class HostRest {

  static final Duration FIRST = Duration.ofHours(1);
  static final Duration LONGEST = Duration.ofHours(24);

  private record Rest(int times, Instant until) {}

  private final Clock clock;
  private final Map<String, Rest> rests = new ConcurrentHashMap<>();

  HostRest(Clock clock) {
    this.clock = clock;
  }

  Duration rest(String host) {
    Rest before = rests.get(host);
    int times = before == null ? 0 : before.times() + 1;
    Duration wait = FIRST.multipliedBy(1L << Math.min(times, 5));
    rests.put(
        host, new Rest(times, clock.instant().plus(wait.compareTo(LONGEST) > 0 ? LONGEST : wait)));
    return wait;
  }

  boolean resting(String host, Instant now) {
    Rest rest = rests.get(host);
    return rest != null && rest.until().isAfter(now);
  }

  void answered(String host) {
    rests.remove(host);
  }
}
