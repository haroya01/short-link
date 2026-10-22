package com.example.short_link.federation.scheduler;

import static com.example.short_link.federation.application.ActivityStreams.idOf;
import static com.example.short_link.federation.application.ActivityStreams.idsOf;

import com.example.short_link.common.note.RemoteNotes;
import com.example.short_link.federation.application.FederationHttp;
import com.example.short_link.federation.application.RemoteActorParser;
import com.example.short_link.federation.application.RemoteNoteParser;
import com.example.short_link.federation.application.relay.RelayBreaker;
import com.example.short_link.federation.application.relay.RelayIntake;
import com.example.short_link.federation.application.relay.RelayProperties;
import com.example.short_link.federation.application.relay.RelayStore;
import com.example.short_link.federation.application.relay.RelaySubscriptions;
import com.example.short_link.federation.application.relay.RelayTally;
import com.example.short_link.federation.application.signature.SigningKeys;
import com.example.short_link.federation.domain.RelayMode;
import com.example.short_link.federation.domain.ServerBlockSeverity;
import com.example.short_link.federation.domain.repository.FederationDomainBlockRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// One low-priority thread works the relay queue, one post at a time and at most per-minute posts a
// minute, so no server is asked twice at once. A post is believed only as its own server gives it,
// and kept only in STORE. The thread does not run where scheduling is off; processNext does the
// same step by hand.
@Slf4j
@Component
public class RelayWorker implements SmartLifecycle {

  private final RelayIntake intake;
  private final RelaySubscriptions subscriptions;
  private final RelayBreaker breaker;
  private final RelayTally tally;
  private final RelayStore store;
  private final RelayProperties props;
  private final RemoteNotes remoteNotes;
  private final FederationDomainBlockRepository serverBlocks;
  private final FederationHttp http;
  private final SigningKeys signingKeys;
  private final RemoteNoteParser parser;
  private final JsonMapper json;
  private final MeterRegistry meters;
  private final boolean scheduling;

  private volatile boolean running;
  private volatile Thread thread;

  public RelayWorker(
      RelayIntake intake,
      RelaySubscriptions subscriptions,
      RelayBreaker breaker,
      RelayTally tally,
      RelayStore store,
      RelayProperties props,
      RemoteNotes remoteNotes,
      FederationDomainBlockRepository serverBlocks,
      FederationHttp http,
      SigningKeys signingKeys,
      RemoteNoteParser parser,
      JsonMapper json,
      MeterRegistry meters,
      @Value("${short-link.scheduling.enabled:true}") boolean scheduling) {
    this.intake = intake;
    this.subscriptions = subscriptions;
    this.breaker = breaker;
    this.tally = tally;
    this.store = store;
    this.props = props;
    this.remoteNotes = remoteNotes;
    this.serverBlocks = serverBlocks;
    this.http = http;
    this.signingKeys = signingKeys;
    this.parser = parser;
    this.json = json;
    this.meters = meters;
    this.scheduling = scheduling;
  }

  @Override
  public void start() {
    running = true;
    if (props.enabled() && scheduling) {
      thread =
          Thread.ofPlatform()
              .name("kurl-relay")
              .daemon()
              .priority(Thread.MIN_PRIORITY)
              .start(this::work);
    }
  }

  @Override
  public void stop() {
    running = false;
    Thread working = thread;
    if (working != null) {
      working.interrupt();
    }
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  public boolean processNext() {
    RelayIntake.Announced announced = intake.poll();
    if (announced == null) {
      return false;
    }
    process(announced);
    return true;
  }

  private void work() {
    long gap = TimeUnit.MINUTES.toNanos(1) / props.perMinute();
    long last = System.nanoTime() - gap;
    while (running) {
      try {
        RelayIntake.Announced announced = intake.next(Duration.ofSeconds(5));
        if (announced == null) {
          continue;
        }
        long wait = last + gap - System.nanoTime();
        if (wait > 0) {
          TimeUnit.NANOSECONDS.sleep(wait);
        }
        last = System.nanoTime();
        process(announced);
      } catch (InterruptedException stopping) {
        Thread.currentThread().interrupt();
        return;
      } catch (RuntimeException failed) {
        log.warn("federation relay step failed reason={}", failed.toString());
      }
    }
  }

  private void process(RelayIntake.Announced announced) {
    long started = System.nanoTime();
    String outcome = outcome(announced);
    Duration took = Duration.ofNanos(System.nanoTime() - started);
    tally.count(outcome);
    meters.timer("federation.relay.process").record(took);
    breaker.processed(took, intake.size());
  }

  private String outcome(RelayIntake.Announced announced) {
    String uri = announced.uri();
    RelaySubscriptions.Setting setting = subscriptions.setting();
    if (setting.mode() == RelayMode.OFF) {
      return "off";
    }
    if (breaker.tripped()) {
      return "tripped";
    }
    if (remoteNotes.exists(uri)) {
      return "kept-already";
    }
    URI address = URI.create(uri);
    Optional<ServerBlockSeverity> blocked = serverBlocks.severityFor(address.getHost());
    if (blocked.isPresent()) {
      return blocked.get() == ServerBlockSeverity.SUSPEND ? "suspended" : "limited";
    }
    if (!(http.get(address, signingKeys.forInstance(), props.patience())
        instanceof FederationHttp.Result.Ok ok)) {
      return "unanswered";
    }
    if (!ok.activityStreams()) {
      return "not-a-post";
    }
    JsonNode post;
    try {
      post = json.readTree(ok.body());
    } catch (JacksonException malformed) {
      return "not-a-post";
    }
    if (post == null || !post.isObject() || RemoteNoteParser.kindOf(post).isEmpty()) {
      return "not-a-post";
    }
    String host = RemoteActorParser.host(uri);
    String id = idOf(post);
    if (id == null
        || !host.equals(RemoteActorParser.host(id))
        || idsOf(post.get("attributedTo")).stream()
            .noneMatch(author -> host.equals(RemoteActorParser.host(author)))) {
      return "foreign";
    }
    RemoteNoteParser.Parsed note = parser.parse(post, post);
    if (!note.visibility().listed()) {
      return "not-public";
    }
    if (note.inReplyToUri() != null) {
      return "reply";
    }
    if (note.sensitive() || note.contentWarning() != null) {
      return "sensitive";
    }
    if (note.language() == null || !setting.languages().contains(note.language())) {
      return "language";
    }
    return setting.mode() == RelayMode.STORE
        ? store.keep(host, post, note, announced.relay())
        : "wanted";
  }
}
