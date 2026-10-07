package com.example.short_link.federation.application;

import com.example.short_link.federation.application.signature.SigningKeys;
import com.example.short_link.federation.domain.RemoteActorDocument;
import com.example.short_link.federation.domain.RemoteActorEntity;
import com.example.short_link.federation.domain.repository.RemoteActorRepository;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
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
@Slf4j
@Service
public class RemoteActorResolver {

  static final Duration FRESH_FOR = Duration.ofDays(1);

  private final FederationHttp http;
  private final SigningKeys signingKeys;
  private final RemoteActorRepository actors;
  private final JsonMapper json;
  private final Clock clock;

  @Autowired
  public RemoteActorResolver(
      FederationHttp http, SigningKeys signingKeys, RemoteActorRepository actors, JsonMapper json) {
    this(http, signingKeys, actors, json, Clock.systemUTC());
  }

  RemoteActorResolver(
      FederationHttp http,
      SigningKeys signingKeys,
      RemoteActorRepository actors,
      JsonMapper json,
      Clock clock) {
    this.http = http;
    this.signingKeys = signingKeys;
    this.actors = actors;
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
      return refetch ? Optional.empty() : cached;
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
    return parsed.map(this::store).or(() -> cached);
  }

  public Optional<RemoteActorEntity> refreshed(RemoteActorEntity cached) {
    return fresh(cached) ? Optional.of(cached) : byActorUri(cached.getActorUri());
  }

  private Optional<JsonNode> fetch(URI uri) {
    return switch (http.get(uri, signingKeys.forInstance())) {
      case FederationHttp.Result.Ok ok -> {
        try {
          yield Optional.of(json.readTree(ok.body()));
        } catch (JacksonException malformed) {
          yield Optional.empty();
        }
      }
      case FederationHttp.Result result -> {
        log.info("federation fetch outcome={} uri={}", result, uri);
        yield Optional.empty();
      }
    };
  }

  private RemoteActorEntity store(RemoteActorDocument document) {
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
    return actor.getFetchedAt().plus(FRESH_FOR).isAfter(clock.instant());
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
