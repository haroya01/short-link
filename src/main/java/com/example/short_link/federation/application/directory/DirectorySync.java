package com.example.short_link.federation.application.directory;

import static com.example.short_link.federation.application.ActivityStreams.text;

import com.example.short_link.federation.application.FederationHttp;
import com.example.short_link.federation.application.RemoteActorParser;
import com.example.short_link.federation.application.RemoteActorResolver;
import com.example.short_link.federation.application.signature.SigningKeys;
import com.example.short_link.federation.domain.FederationDirectorySourceEntity;
import com.example.short_link.federation.domain.RemoteActorEntity;
import com.example.short_link.federation.domain.repository.FederationDirectorySourceRepository;
import com.example.short_link.federation.domain.repository.FederationDomainBlockRepository;
import com.example.short_link.federation.domain.repository.RemoteActorRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// One request at a time with a pause between. An account is stored only as its own server gives
// its actor. Anyone listed before with no word of consent for the consent window, from a directory
// or from their own server, stops being findable by name.
@Slf4j
@Service
public class DirectorySync {

  static final int PAGE = 80;
  static final List<Duration> BACKOFF =
      List.of(Duration.ofHours(1), Duration.ofHours(6), Duration.ofHours(24));

  private final FederationDirectorySourceRepository sources;
  private final RemoteActorRepository actors;
  private final RemoteActorResolver resolver;
  private final FederationDomainBlockRepository serverBlocks;
  private final FederationHttp http;
  private final SigningKeys signingKeys;
  private final JsonMapper json;
  private final DirectoryProperties props;
  private final DirectoryPacer pacer;
  private final MeterRegistry meters;
  private final Clock clock;

  public record Outcome(
      String host, int listed, int known, int added, int later, int skipped, String failure) {

    String summary() {
      String counts =
          "listed %d, known %d, added %d, later %d, skipped %d"
              .formatted(listed, known, added, later, skipped);
      return failure == null ? counts : failure + "; " + counts;
    }
  }

  public DirectorySync(
      FederationDirectorySourceRepository sources,
      RemoteActorRepository actors,
      RemoteActorResolver resolver,
      FederationDomainBlockRepository serverBlocks,
      FederationHttp http,
      SigningKeys signingKeys,
      JsonMapper json,
      DirectoryProperties props,
      DirectoryPacer pacer,
      MeterRegistry meters,
      Clock clock) {
    this.sources = sources;
    this.actors = actors;
    this.resolver = resolver;
    this.serverBlocks = serverBlocks;
    this.http = http;
    this.signingKeys = signingKeys;
    this.json = json;
    this.props = props;
    this.pacer = pacer;
    this.meters = meters;
    this.clock = clock;
  }

  public List<Outcome> run() {
    Instant started = clock.instant();
    List<Outcome> outcomes = new ArrayList<>();
    for (FederationDirectorySourceEntity source : sources.findAllByOrderByHostAsc()) {
      if (source.isEnabled()
          && (source.getRetryAfter() == null || !started.isBefore(source.getRetryAfter()))) {
        try {
          outcomes.add(sync(source.getHost()));
        } catch (RuntimeException failed) {
          log.warn(
              "federation directory host={} failed reason={}", source.getHost(), failed.toString());
        }
      }
    }
    int forgotten = actors.forgetUnlisted(clock.instant().minus(props.consent()));
    meters.counter("federation.directory", "outcome", "forgotten").increment(forgotten);
    log.info("federation directory run sources={} forgotten={}", outcomes.size(), forgotten);
    return outcomes;
  }

  public boolean unreadSince(Instant since) {
    Instant now = clock.instant();
    return sources.findAllByOrderByHostAsc().stream()
        .anyMatch(
            source ->
                source.isEnabled()
                    && (source.getRetryAfter() == null || !now.isBefore(source.getRetryAfter()))
                    && (source.getLastSyncedAt() == null
                        || source.getLastSyncedAt().isBefore(since)));
  }

  public Outcome sync(String host) {
    Optional<FederationDirectorySourceEntity> found = sources.findById(host);
    if (found.isEmpty()) {
      return new Outcome(host, 0, 0, 0, 0, 0, "gone");
    }
    FederationDirectorySourceEntity source = found.get();
    if (serverBlocks.severityFor(host).isPresent()) {
      return record(source, new Outcome(host, 0, 0, 0, 0, 0, "blocked here"));
    }
    int listed = 0;
    int known = 0;
    int added = 0;
    int later = 0;
    int skipped = 0;
    int fetches = 0;
    for (int offset = 0; offset < source.getCap(); offset += PAGE) {
      if (offset > 0) {
        pacer.pause(props.pageGap());
      }
      int limit = Math.min(PAGE, source.getCap() - offset);
      URI page =
          URI.create(
              "https://"
                  + host
                  + "/api/v1/directory?local=true&order=active&limit="
                  + limit
                  + "&offset="
                  + offset);
      FederationHttp.Result answer =
          http.getJson(page, signingKeys.forInstance(), props.pagePatience());
      JsonNode accounts = answer instanceof FederationHttp.Result.Ok ok ? accounts(ok) : null;
      if (accounts == null) {
        return record(
            source, new Outcome(host, listed, known, added, later, skipped, failure(answer)));
      }
      Set<String> wanted = new LinkedHashSet<>();
      for (JsonNode account : accounts) {
        listed++;
        String uri = text(account.get("uri"));
        if (uri == null
            || !host.equals(RemoteActorParser.host(uri))
            || !account.path("discoverable").asBoolean(false)
            || account.hasNonNull("moved")) {
          skipped++;
        } else {
          wanted.add(uri);
        }
      }
      Map<String, RemoteActorEntity> stored =
          actors.findAllByActorUri(wanted).stream()
              .collect(Collectors.toMap(RemoteActorEntity::getActorUri, Function.identity()));
      List<Long> seen = new ArrayList<>();
      for (String uri : wanted) {
        RemoteActorEntity actor = stored.get(uri);
        if (actor != null) {
          known++;
          seen.add(actor.getId());
          continue;
        }
        if (fetches >= props.newPerRun()) {
          later++;
          continue;
        }
        fetches++;
        pacer.pause(props.fetchGap());
        Optional<RemoteActorEntity> fetched =
            resolver.byActorUri(uri).filter(remote -> !remote.onSuspendedServer());
        if (fetched.isPresent()) {
          added++;
          seen.add(fetched.get().getId());
        } else {
          skipped++;
        }
      }
      actors.markListed(seen, clock.instant());
      if (accounts.size() < limit) {
        break;
      }
    }
    return record(source, new Outcome(host, listed, known, added, later, skipped, null));
  }

  private JsonNode accounts(FederationHttp.Result.Ok ok) {
    try {
      JsonNode page = json.readTree(ok.body());
      return page != null && page.isArray() ? page : null;
    } catch (JacksonException malformed) {
      return null;
    }
  }

  private static String failure(FederationHttp.Result answer) {
    return switch (answer) {
      case FederationHttp.Result.Ok ok -> "not a directory";
      case FederationHttp.Result.Failed failed -> "HTTP " + failed.status();
      case FederationHttp.Result.Refused refused -> "refused: " + refused.reason();
      case FederationHttp.Result.Unreachable unreachable -> "unreachable: " + unreachable.reason();
    };
  }

  private Outcome record(FederationDirectorySourceEntity source, Outcome outcome) {
    Instant now = clock.instant();
    if (outcome.failure() == null) {
      source.synced(outcome.summary(), now);
    } else {
      source.failed(
          outcome.summary(),
          now.plus(BACKOFF.get(Math.min(source.getFailures(), BACKOFF.size() - 1))));
    }
    sources.save(source);
    meters.counter("federation.directory", "outcome", "added").increment(outcome.added());
    meters.counter("federation.directory", "outcome", "known").increment(outcome.known());
    meters.counter("federation.directory", "outcome", "later").increment(outcome.later());
    meters.counter("federation.directory", "outcome", "skipped").increment(outcome.skipped());
    log.info("federation directory host={} {}", outcome.host(), outcome.summary());
    return outcome;
  }
}
