package archfixtures.domain;

import java.time.Clock;
import java.time.Instant;

/** Sits in a ..domain.. package so the wall-clock rule applies to it. */
public final class ClockFixtures {

  private ClockFixtures() {}

  public static class ReadsWallClockDirectly {
    public Instant run() {
      return Instant.now();
    }
  }

  public static class TakesTimeFromTheClock {
    public Instant run(Clock clock) {
      return clock.instant();
    }
  }
}
