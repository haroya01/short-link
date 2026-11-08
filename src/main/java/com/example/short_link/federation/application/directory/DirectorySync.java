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
    Tally tally = new Tally();
    for (int offset = 0; offset < source.getCap(); offset += PAGE) {
      if (offset > 0) {
        pacer.pause(props.pageGap());
      }
      int limit = Math.min(PAGE, source.getCap() - offset);
      FederationHttp.Result answer =
          http.getJson(page(host, limit, offset), signingKeys.forInstance(), props.pagePatience());
      JsonNode accounts = answer instanceof FederationHttp.Result.Ok ok ? accounts(ok) : null;
      if (accounts == null) {
        return record(source, tally.outcome(host, failure(answer)));
      }
      actors.markListed(listed(host, accounts, tally), clock.instant());
      if (accounts.size() < limit) {
        break;
      }
    }
    return record(source, tally.outcome(host, null));
  }

  private static final class Tally {

    private int listed;
    private int known;
    private int added;
    private int later;
    private int skipped;
    private int fetches;

    Outcome outcome(String host, String failure) {
      return new Outcome(host, listed, known, added, later, skipped, failure);
    }
  }

  private static URI page(String host, int limit, int offset) {
    return URI.create(
        "https://"
            + host
            + "/api/v1/directory?local=true&order=active&limit="
            + limit
            + "&offset="
            + offset);
  }

  private List<Long> listed(String host, JsonNode accounts, Tally tally) {
    Set<String> wanted = wanted(host, accounts, tally);
    Map<String, RemoteActorEntity> stored =
        actors.findAllByActorUri(wanted).stream()
            .collect(Collectors.toMap(RemoteActorEntity::getActorUri, Function.identity()));
    List<Long> seen = new ArrayList<>();
    for (String uri : wanted) {
      RemoteActorEntity actor = stored.get(uri);
      if (actor != null) {
        tally.known++;
        seen.add(actor.getId());
      } else {
        fetch(uri, tally).ifPresent(seen::add);
      }
    }
    return seen;
  }

  private static Set<String> wanted(String host, JsonNode accounts, Tally tally) {
    Set<String> wanted = new LinkedHashSet<>();
    for (JsonNode account : accounts) {
      tally.listed++;
      String uri = text(account.get("uri"));
      if (findable(host, uri, account)) {
        wanted.add(uri);
      } else {
        tally.skipped++;
      }
    }
    return wanted;
  }

  private static boolean findable(String host, String uri, JsonNode account) {
    return uri != null
        && host.equals(RemoteActorParser.host(uri))
        && account.path("discoverable").asBoolean(false)
        && !account.hasNonNull("moved");
  }

  private Optional<Long> fetch(String uri, Tally tally) {
    if (tally.fetches >= props.newPerRun()) {
      tally.later++;
      return Optional.empty();
    }
    tally.fetches++;
    pacer.pause(props.fetchGap());
    Optional<RemoteActorEntity> fetched =
        resolver.byActorUri(uri).filter(remote -> !remote.onSuspendedServer());
    if (fetched.isEmpty()) {
      tally.skipped++;
      return Optional.empty();
    }
    tally.added++;
    return Optional.of(fetched.get().getId());
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
