package com.example.short_link.federation.application.inbox;

import static com.example.short_link.federation.application.ActivityStreams.idOf;
import static com.example.short_link.federation.application.ActivityStreams.text;

import com.example.short_link.federation.application.FederationActorService;
import com.example.short_link.federation.application.FederationFollowers;
import com.example.short_link.federation.application.FederationUrls;
import com.example.short_link.federation.application.LocalActor;
import com.example.short_link.federation.application.RemoteActorParser;
import com.example.short_link.federation.domain.FederationActorEntity;
import com.example.short_link.federation.domain.RemoteActorEntity;
import com.example.short_link.federation.domain.repository.FederationActorRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// Activities we do not act on are acknowledged without a signature check and never cost a key
// fetch; anything we act on must verify first.
@Slf4j
@Service
@RequiredArgsConstructor
public class InboxService {

  private final InboxVerifier verifier;
  private final FederationActorService localActors;
  private final FederationActorRepository actorRows;
  private final FederationFollowers followers;
  private final FederationUrls urls;
  private final JsonMapper json;
  private final MeterRegistry meters;

  private sealed interface Intent {
    record Follow(LocalActor target) implements Intent {}

    record Unfollow(String targetPublicId, String followId) implements Intent {}

    record Forget() implements Intent {}
  }

  public InboxOutcome receive(InboxMessage request, String inboxPublicId) {
    InboxOutcome outcome = handle(request, inboxPublicId);
    meters
        .counter(
            "federation.inbox",
            "outcome",
            outcome.kind().name().toLowerCase(Locale.ROOT),
            "reason",
            outcome.reason())
        .increment();
    if (outcome.kind() == InboxOutcome.Kind.UNAUTHORIZED
        || outcome.kind() == InboxOutcome.Kind.MALFORMED) {
      log.info("federation inbox outcome={} reason={}", outcome.kind(), outcome.reason());
    }
    return outcome;
  }

  private InboxOutcome handle(InboxMessage request, String inboxPublicId) {
    Optional<LocalActor> owner = Optional.empty();
    if (inboxPublicId != null) {
      owner = localActors.byPublicId(inboxPublicId);
      if (owner.isEmpty()) {
        return InboxOutcome.notFound();
      }
    }
    JsonNode activity;
    try {
      activity = json.readTree(request.body());
    } catch (JacksonException malformed) {
      return InboxOutcome.malformed("json");
    }
    if (activity == null || !activity.isObject()) {
      return InboxOutcome.malformed("json");
    }
    String type = text(activity.get("type"));
    String id = text(activity.get("id"));
    String actorUri = idOf(activity.get("actor"));
    if (type == null || id == null || actorUri == null) {
      return InboxOutcome.malformed("fields");
    }

    Intent intent;
    switch (type) {
      case "Follow" -> {
        Optional<LocalActor> target = followTarget(activity.get("object"), owner);
        if (target.isEmpty()) {
          return InboxOutcome.ignored("unknown-target");
        }
        intent = new Intent.Follow(target.get());
      }
      case "Undo" -> {
        JsonNode object = activity.get("object");
        if (object != null && object.isObject()) {
          if (!"Follow".equals(text(object.get("type")))) {
            return InboxOutcome.ignored("unsupported");
          }
          if (!actorUri.equals(idOf(object.get("actor")))) {
            return InboxOutcome.ignored("actor-mismatch");
          }
        }
        String followId = idOf(object);
        if (followId == null) {
          return InboxOutcome.malformed("fields");
        }
        String target =
            object.isObject() ? urls.publicIdOf(idOf(object.get("object"))).orElse(null) : null;
        intent = new Intent.Unfollow(target, followId);
      }
      case "Delete" -> {
        if (!actorUri.equals(idOf(activity.get("object")))) {
          return InboxOutcome.ignored("unsupported");
        }
        intent = new Intent.Forget();
      }
      default -> {
        return InboxOutcome.ignored("unsupported");
      }
    }

    boolean forget = intent instanceof Intent.Forget;
    RemoteActorEntity actor;
    switch (verifier.verify(request, forget)) {
      case InboxVerifier.Result.Rejected rejected -> {
        return forget && rejected.reason().equals("unknown-key")
            ? InboxOutcome.ignored("gone-actor")
            : InboxOutcome.unauthorized(rejected.reason());
      }
      case InboxVerifier.Result.Verified verified -> actor = verified.actor();
    }
    if (!actor.getActorUri().equals(actorUri)) {
      return InboxOutcome.ignored("actor-mismatch");
    }
    if (!Objects.equals(RemoteActorParser.host(id), actor.getDomain())) {
      return InboxOutcome.ignored("foreign-id");
    }

    return switch (intent) {
      case Intent.Follow follow -> {
        followers.follow(follow.target(), actor, id);
        yield InboxOutcome.accepted("follow");
      }
      case Intent.Unfollow unfollow -> {
        Optional<Long> userId =
            Optional.ofNullable(unfollow.targetPublicId())
                .flatMap(actorRows::findByPublicId)
                .map(FederationActorEntity::getUserId);
        if (userId.isPresent()) {
          followers.unfollow(userId.get(), actor);
        } else {
          followers.unfollow(actor, unfollow.followId());
        }
        yield InboxOutcome.accepted("undo-follow");
      }
      case Intent.Forget gone -> {
        followers.forget(actor);
        yield InboxOutcome.accepted("delete-actor");
      }
    };
  }

  private Optional<LocalActor> followTarget(JsonNode object, Optional<LocalActor> owner) {
    Optional<String> publicId = urls.publicIdOf(idOf(object));
    if (publicId.isEmpty()) {
      return Optional.empty();
    }
    if (owner.isPresent() && owner.get().publicId().equals(publicId.get())) {
      return owner;
    }
    return localActors.byPublicId(publicId.get());
  }
}
