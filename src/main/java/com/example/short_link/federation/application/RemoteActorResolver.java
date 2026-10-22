package com.example.short_link.federation.application;

import com.example.short_link.federation.application.signature.SigningKeys;
import com.example.short_link.federation.domain.RemoteActorDocument;
import com.example.short_link.federation.domain.RemoteActorEntity;
import com.example.short_link.federation.domain.repository.FederationDomainBlockRepository;
import com.example.short_link.federation.domain.repository.RemoteActorRepository;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// Key ids differ by server (Mastodon "<actor>#main-key", GoToSocial "<actor>/main-key"), so a key
// is resolved by fetching it and following its owner to the full actor document. Cached actors are
// refetched after a day, or at once when a signature fails (the remote may have rotated its key).
// A day-old copy its server did not give again is kept as is for an hour; that hour is held in
// process memory only, so a restart asks again at once.
@Slf4j
@Service
public class RemoteActorResolver {

  static final Duration FRESH_FOR = Duration.ofDays(1);
  static final Duration RETRY_AFTER = Duration.ofHours(1);

  private final FederationHttp http;
  private final SigningKeys signingKeys;
  private final RemoteActorRepository actors;
  private final FederationDomainBlockRepository serverBlocks;
  private final JsonMapper json;
  private final Clock clock;
  private final Map<String, Instant> unanswered = new ConcurrentHashMap<>();

  @Autowired
  public RemoteActorResolver(
      FederationHttp http,
      SigningKeys signingKeys,
      RemoteActorRepository actors,
      FederationDomainBlockRepository serverBlocks,
      JsonMapper json) {
    this(http, signingKeys, actors, serverBlocks, json, Clock.systemUTC());
  }

  RemoteActorResolver(
      FederationHttp http,
      SigningKeys signingKeys,
      RemoteActorRepository actors,
      FederationDomainBlockRepository serverBlocks,
      JsonMapper json,
      Clock clock) {
    this.http = http;
    this.signingKeys = signingKeys;
    this.actors = actors;
    this.serverBlocks = serverBlocks;
    this.json = json;
    this.clock = clock;
  }

  public Optional<RemoteActorEntity> byKeyId(String keyId, boolean refetch) {
    Optional<RemoteActorEntity> cached = actors.findByKeyId(keyId);
    if (cached.isPresent() && !refetch && fresh(cached.get())) {
      return cached;
    }
    URI keyUri = withoutFragment(keyId);
    if (keyUri == null) {
      return Optional.empty();
    }
    Optional<JsonNode> keyDocument = fetch(keyUri);
    if (keyDocument.isEmpty()) {
      return refetch ? Optional.empty() : unanswered(cached);
    }
    Optional<RemoteActorDocument> parsed =
        RemoteActorParser.parse(keyDocument.get(), keyUri, keyId);
    if (parsed.isEmpty()) {
      Optional<String> owner = RemoteActorParser.keyOwner(keyDocument.get(), keyId);
      URI ownerUri = owner.map(RemoteActorResolver::withoutFragment).orElse(null);
      if (ownerUri == null || !sameHost(ownerUri, keyUri)) {
        return Optional.empty();
      }
      parsed = fetch(ownerUri).flatMap(doc -> RemoteActorParser.parse(doc, ownerUri, keyId));
    }
    return parsed.map(this::store);
  }

  public Optional<RemoteActorEntity> byActorUri(String actorUri) {
    Optional<RemoteActorEntity> cached = actors.findByActorUri(actorUri);
    if (cached.isPresent() && fresh(cached.get())) {
      return cached;
    }
    URI uri = withoutFragment(actorUri);
    if (uri == null) {
      return Optional.empty();
    }
    Optional<RemoteActorDocument> parsed =
        fetch(uri).flatMap(doc -> RemoteActorParser.parse(doc, uri, null));
    return parsed.map(this::store).or(() -> unanswered(cached));
  }

  public Optional<RemoteActorEntity> refreshed(RemoteActorEntity cached) {
    return fresh(cached) ? Optional.of(cached) : byActorUri(cached.getActorUri());
  }

  // A searched address on the actor's server may serve it under another address (a profile page).
  // That document is believed only as its own id serves it.
  public Optional<RemoteActorEntity> stored(JsonNode document, URI fetchedFrom) {
    Optional<RemoteActorDocument> parsed = RemoteActorParser.parse(document, fetchedFrom, null);
    if (parsed.isEmpty()) {
      URI id =
          RemoteActorParser.id(document).map(RemoteActorResolver::withoutFragment).orElse(null);
      if (id != null
          && sameHost(id, fetchedFrom)
          && !id.equals(withoutFragment(fetchedFrom.toString()))) {
        parsed = fetch(id).flatMap(doc -> RemoteActorParser.parse(doc, id, null));
      }
    }
    return parsed.map(this::store);
  }

  // A suspended server is never asked for anything.
  private Optional<JsonNode> fetch(URI uri) {
    if (serverBlocks.suspends(uri.getHost())) {
      return Optional.empty();
    }
    return switch (http.get(uri, signingKeys.forInstance())) {
      case FederationHttp.Result.Ok ok when !ok.activityStreams() -> {
        log.info("federation fetch not activity json type={} uri={}", ok.contentType(), uri);
        yield Optional.empty();
      }
      case FederationHttp.Result.Ok ok -> {
        try {
          yield Optional.of(json.readTree(ok.body()));
        } catch (JacksonException malformed) {
          yield Optional.empty();
        }
      }
      case FederationHttp.Result.Failed failed when failed.deniedAccess() -> {
        log.warn("federation fetch denied status={} host={}", failed.status(), uri.getHost());
        yield Optional.empty();
      }
      case FederationHttp.Result result -> {
        log.info("federation fetch outcome={} uri={}", result, uri);
        yield Optional.empty();
      }
    };
  }

  private Optional<RemoteActorEntity> unanswered(Optional<RemoteActorEntity> cached) {
    cached.ifPresent(
        actor -> unanswered.put(actor.getActorUri(), clock.instant().plus(RETRY_AFTER)));
    return cached;
  }

  private RemoteActorEntity store(RemoteActorDocument document) {
    unanswered.remove(document.actorUri());
    Optional<RemoteActorEntity> existing = actors.findByActorUri(document.actorUri());
    if (existing.isPresent()) {
      existing.get().refresh(document, clock.instant());
      return actors.saveAndFlush(existing.get());
    }
    try {
      return actors.saveAndFlush(new RemoteActorEntity(document, clock.instant()));
    } catch (DataIntegrityViolationException raced) {
      return actors.findByActorUri(document.actorUri()).orElseThrow(() -> raced);
    }
  }

  private boolean fresh(RemoteActorEntity actor) {
    Instant now = clock.instant();
    if (actor.getFetchedAt().plus(FRESH_FOR).isAfter(now)) {
      return true;
    }
    Instant retryAt = unanswered.get(actor.getActorUri());
    return retryAt != null && now.isBefore(retryAt);
  }

  private static boolean sameHost(URI a, URI b) {
    return a.getHost() != null
        && a.getHost().equalsIgnoreCase(b.getHost())
        && a.getPort() == b.getPort();
  }

  static URI withoutFragment(String uri) {
    if (uri == null) {
      return null;
    }
    int hash = uri.indexOf('#');
    try {
      URI parsed = URI.create(hash < 0 ? uri : uri.substring(0, hash));
      return parsed.getScheme() == null || parsed.getHost() == null ? null : parsed;
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
