package com.example.short_link.federation.application.relay;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// Stops all relay work by itself when the queue stays four-fifths full for five minutes or five
// posts in a row each take longer than slow-after; an ERROR line reaches the alerts. Only an admin
// changing the relay setting starts it again, and a STORE setting has become COUNT by then.
@Slf4j
@Component
public class RelayBreaker {

  static final Duration BACKLOG_FOR = Duration.ofMinutes(5);
  static final int SLOW_IN_A_ROW = 5;

  private final RelayProperties props;
  private final RelayTally tally;
  private final RelaySubscriptions subscriptions;
  private final Clock clock;

  private volatile boolean tripped;
  private Instant backlogSince;
  private int slowInARow;

  public RelayBreaker(
      RelayProperties props, RelayTally tally, RelaySubscriptions subscriptions, Clock clock) {
    this.props = props;
    this.tally = tally;
    this.subscriptions = subscriptions;
    this.clock = clock;
  }

  public boolean tripped() {
    return tripped;
  }

  public synchronized void processed(Duration took, int backlog) {
    slowInARow = took.compareTo(props.slowAfter()) > 0 ? slowInARow + 1 : 0;
    if (slowInARow >= SLOW_IN_A_ROW) {
      trip("slow");
    }
    queued(backlog);
  }

  public synchronized void queued(int backlog) {
    if (backlog * 5 < props.queue() * 4) {
      backlogSince = null;
      return;
    }
    Instant now = clock.instant();
    if (backlogSince == null) {
      backlogSince = now;
    } else if (!now.isBefore(backlogSince.plus(BACKLOG_FOR))) {
      trip("backlog");
    }
  }

  public synchronized void rearm() {
    tripped = false;
    backlogSince = null;
    slowInARow = 0;
  }

  private void trip(String reason) {
    if (tripped) {
      return;
    }
    tripped = true;
    tally.count("tripped-" + reason);
    log.error("federation relay stopped itself reason={} queue={}", reason, props.queue());
    try {
      subscriptions.stopStoring();
    } catch (RuntimeException failed) {
      log.error("federation relay could not leave STORE reason={}", failed.toString());
    }
  }
}
