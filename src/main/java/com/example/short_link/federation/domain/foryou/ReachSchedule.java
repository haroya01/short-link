package com.example.short_link.federation.domain.foryou;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public final class ReachSchedule {

  public static final Duration FIRST = Duration.ofHours(1);
  public static final Duration LATE = Duration.ofHours(6);
  public static final int MAX_FAILURES = 3;

  private static final List<Duration> AFTER_CHECK =
      List.of(Duration.ofHours(5), Duration.ofHours(18));

  private ReachSchedule() {}

  public static Instant first(Instant keptAt) {
    return keptAt.plus(FIRST);
  }

  public static Instant afterCheck(int checks, Instant now) {
    return checks <= AFTER_CHECK.size() ? now.plus(AFTER_CHECK.get(checks - 1)) : null;
  }

  public static Instant afterFailure(int failures, Instant now) {
    return failures >= MAX_FAILURES ? null : now.plus(FIRST.multipliedBy(1L << failures));
  }
}
