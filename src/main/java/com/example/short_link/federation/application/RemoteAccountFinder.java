package com.example.short_link.federation.application;

import com.example.short_link.federation.application.signature.SigningKeys;
import com.example.short_link.federation.domain.RemoteActorEntity;
import com.example.short_link.federation.domain.repository.RemoteActorRepository;
import com.example.short_link.federation.exception.FederationErrorCode;
import com.example.short_link.federation.exception.FederationException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class RemoteAccountFinder {

  private static final Pattern HANDLE =
      Pattern.compile(
          "@?([A-Za-z0-9_][A-Za-z0-9_.-]{0,63})@([A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+(?::[0-9]{1,5})?)");

  private static final Set<String> ACTIVITY_TYPES =
      Set.of(
          "application/activity+json",
          "application/ld+json; profile=\"https://www.w3.org/ns/activitystreams\"");

  private final FederationHttp http;
  private final SigningKeys signingKeys;
  private final RemoteActorRepository actors;
  private final RemoteActorResolver resolver;
  private final FederationUrls urls;
  private final JsonMapper json;
  private final RemoteDocuments documents;

  public record Handle(String username, String domain) {}

  public static Handle parse(String raw) {
    Matcher m = HANDLE.matcher(raw == null ? "" : raw.strip());
    if (!m.matches()) {
      throw new FederationException(FederationErrorCode.REMOTE_ACCOUNT_INVALID);
    }
    return new Handle(m.group(1), m.group(2).toLowerCase(Locale.ROOT));
  }

  public Optional<RemoteActorEntity> find(String raw) {
    Handle handle = parse(raw);
    if (handle.domain().equals(urls.domain())) {
      return Optional.empty();
    }
    Optional<RemoteActorEntity> cached = actors.findByAcct(handle.username(), handle.domain());
    if (cached.isPresent()) {
      return resolver.refreshed(cached.get());
    }
    return webFinger(handle).flatMap(resolver::byActorUri);
  }

  public RemoteActorEntity resolve(String raw) {
    Handle handle = parse(raw);
    String asked = handle.username() + "@" + handle.domain();
    if (handle.domain().equals(urls.domain())) {
      throw RemoteDocuments.notFound(asked);
    }
    Optional<RemoteActorEntity> cached = actors.findByAcct(handle.username(), handle.domain());
    RemoteActorEntity actor =
        cached.isPresent()
            ? resolver.refreshed(cached.get()).orElse(cached.get())
            : located(handle, asked);
    if (actor.onSuspendedServer()) {
      throw RemoteDocuments.notFound(asked);
    }
    return actor;
  }

  private RemoteActorEntity located(Handle handle, String asked) {
    URI actorUri =
        selfLink(documents.fetch(webFingerUri(handle), asked))
            .map(RemoteActorResolver::withoutFragment)
            .orElseThrow(() -> RemoteDocuments.notFound(asked));
    return resolver
        .stored(documents.fetch(actorUri, asked), actorUri)
        .orElseThrow(() -> RemoteDocuments.notFound(asked));
  }

  private Optional<String> webFinger(Handle handle) {
    if (!(http.get(webFingerUri(handle), signingKeys.forInstance())
        instanceof FederationHttp.Result.Ok ok)) {
      return Optional.empty();
    }
    JsonNode document;
    try {
      document = json.readTree(ok.body());
    } catch (JacksonException malformed) {
      log.info("federation webfinger malformed host={}", handle.domain());
      return Optional.empty();
    }
    return document == null ? Optional.empty() : selfLink(document);
  }

  private static URI webFingerUri(Handle handle) {
    String resource = "acct:" + handle.username() + "@" + handle.domain();
    return URI.create(
        "https://"
            + handle.domain()
            + "/.well-known/webfinger?resource="
            + URLEncoder.encode(resource, StandardCharsets.UTF_8));
  }

  private static Optional<String> selfLink(JsonNode document) {
    JsonNode links = document.get("links");
    if (links == null || !links.isArray()) {
      return Optional.empty();
    }
    for (JsonNode link : links) {
      if ("self".equals(ActivityStreams.text(link.get("rel")))
          && ACTIVITY_TYPES.contains(ActivityStreams.text(link.get("type")))) {
        return Optional.ofNullable(ActivityStreams.text(link.get("href")));
      }
    }
    return Optional.empty();
  }
}
