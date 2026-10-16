package com.example.short_link.federation.application;

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
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

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

  private final RemoteActorRepository actors;
  private final RemoteActorResolver resolver;
  private final FederationUrls urls;
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
    return webFinger(handle).flatMap(resolver::byActorUri).map(actor -> knownAt(actor, handle));
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
            : knownAt(located(handle, asked), handle);
    if (actor.onSuspendedServer()) {
      throw RemoteDocuments.notFound(asked);
    }
    return actor;
  }

  private RemoteActorEntity located(Handle handle, String asked) {
    URI actorUri =
        selfLink(documents.webFinger(webFingerUri(handle), asked))
            .map(RemoteActorResolver::withoutFragment)
            .orElseThrow(() -> RemoteDocuments.notFound(asked));
    return resolver
        .stored(documents.fetch(actorUri, asked), actorUri)
        .orElseThrow(() -> RemoteDocuments.notFound(asked));
  }

  private Optional<String> webFinger(Handle handle) {
    try {
      return selfLink(
          documents.webFinger(webFingerUri(handle), handle.username() + "@" + handle.domain()));
    } catch (FederationException unanswered) {
      return Optional.empty();
    }
  }

  // An account served from another host is known by the asked handle only when that host's own
  // WebFinger gives the handle as its subject and the same actor as its self link.
  private RemoteActorEntity knownAt(RemoteActorEntity actor, Handle handle) {
    if (handle.domain().equals(actor.getDomain())
        || !handle.username().equalsIgnoreCase(actor.getUsername())) {
      return actor;
    }
    Handle served = new Handle(actor.getUsername(), actor.getDomain());
    JsonNode answer;
    try {
      answer = documents.webFinger(webFingerUri(served), served.username() + "@" + served.domain());
    } catch (FederationException unanswered) {
      return actor;
    }
    String subject = ActivityStreams.text(answer.get("subject"));
    boolean confirmed =
        ("acct:" + handle.username() + "@" + handle.domain()).equalsIgnoreCase(subject)
            && selfLink(answer)
                .map(RemoteActorResolver::withoutFragment)
                .map(URI::toString)
                .filter(actor.getActorUri()::equals)
                .isPresent();
    if (!confirmed) {
      return actor;
    }
    actor.knownAt(handle.domain());
    return actors.saveAndFlush(actor);
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
