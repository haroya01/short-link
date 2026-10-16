package com.example.short_link.federation.application.relay;

import com.example.short_link.federation.application.FederationUrls;
import com.example.short_link.federation.application.RemoteActorParser;
import com.example.short_link.federation.domain.RelayMode;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

// The inbox only judges an announce by its object's address and hands it to one worker: the reply
// goes out at once, whatever the worker is doing. A full queue lets the oldest address go, and no
// more are taken once a day's worth has been.
@Component
public class RelayIntake {

  static final int MAX_URI = 512;

  private final RelaySubscriptions subscriptions;
  private final RelayBreaker breaker;
  private final RelayTally tally;
  private final RelayProperties props;
  private final FederationUrls urls;
  private final LinkedBlockingDeque<String> queue;

  public RelayIntake(
      RelaySubscriptions subscriptions,
      RelayBreaker breaker,
      RelayTally tally,
      RelayProperties props,
      FederationUrls urls,
      MeterRegistry meters) {
    this.subscriptions = subscriptions;
    this.breaker = breaker;
    this.tally = tally;
    this.props = props;
    this.urls = urls;
    this.queue = new LinkedBlockingDeque<>(props.queue());
    Gauge.builder("federation.relay.queue", queue, LinkedBlockingDeque::size).register(meters);
  }

  public String offer(String objectUri) {
    String outcome = judged(objectUri);
    tally.count(outcome);
    return outcome;
  }

  public String poll() {
    return queue.pollFirst();
  }

  public String next(Duration wait) throws InterruptedException {
    return queue.pollFirst(wait.toMillis(), TimeUnit.MILLISECONDS);
  }

  public int size() {
    return queue.size();
  }

  private String judged(String objectUri) {
    if (subscriptions.setting().mode() == RelayMode.OFF) {
      return "off";
    }
    if (breaker.tripped()) {
      return "tripped";
    }
    String host = objectUri == null ? null : RemoteActorParser.host(objectUri);
    if (host == null || objectUri.length() > MAX_URI || !objectUri.startsWith("https://")) {
      return "malformed";
    }
    if (urls.isOurs(objectUri)) {
      return "own-server";
    }
    if (tally.today("queued") >= props.perDay()) {
      return "daily-cap";
    }
    while (!queue.offerLast(objectUri)) {
      if (queue.pollFirst() != null) {
        tally.count("queue-full");
      }
    }
    breaker.queued(queue.size());
    return "queued";
  }
}
