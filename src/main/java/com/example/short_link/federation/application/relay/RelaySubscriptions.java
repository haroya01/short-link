package com.example.short_link.federation.application.relay;

import com.example.short_link.federation.application.ActivityStreams;
import com.example.short_link.federation.application.FederationUrls;
import com.example.short_link.federation.application.RemoteActorResolver;
import com.example.short_link.federation.application.delivery.DeliveryQueue;
import com.example.short_link.federation.domain.FederationRelayEntity;
import com.example.short_link.federation.domain.FederationRelaySettingEntity;
import com.example.short_link.federation.domain.RelayMode;
import com.example.short_link.federation.domain.RelayState;
import com.example.short_link.federation.domain.RemoteActorEntity;
import com.example.short_link.federation.domain.repository.FederationRelayRepository;
import com.example.short_link.federation.exception.FederationErrorCode;
import com.example.short_link.federation.exception.FederationException;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

// The instance actor follows a relay with the Public collection as the object, as Mastodon does.
// Which relays have accepted, and the one setting, are held in memory so an announce costs no query
// to recognise.
@Service
public class RelaySubscriptions {

  private final FederationRelayRepository relays;
  private final RemoteActorResolver resolver;
  private final DeliveryQueue deliveries;
  private final FederationUrls urls;
  private final JsonMapper json;
  private final RelayProperties props;
  private final Clock clock;

  private volatile Set<String> accepted = Set.of();
  private volatile Setting setting = new Setting(RelayMode.OFF, List.of());

  public record Setting(RelayMode mode, List<String> languages) {}

  public record View(
      Long id,
      String actorUri,
      String label,
      RelayState state,
      Instant acceptedAt,
      Instant createdAt) {

    static View of(FederationRelayEntity row) {
      return new View(
          row.getId(),
          row.getActorUri(),
          row.getLabel(),
          row.getState(),
          row.getAcceptedAt(),
          row.getCreatedAt());
    }
  }

  @Autowired
  public RelaySubscriptions(
      FederationRelayRepository relays,
      RemoteActorResolver resolver,
      DeliveryQueue deliveries,
      FederationUrls urls,
      JsonMapper json,
      RelayProperties props) {
    this(relays, resolver, deliveries, urls, json, props, Clock.systemUTC());
  }

  RelaySubscriptions(
      FederationRelayRepository relays,
      RemoteActorResolver resolver,
      DeliveryQueue deliveries,
      FederationUrls urls,
      JsonMapper json,
      RelayProperties props,
      Clock clock) {
    this.relays = relays;
    this.resolver = resolver;
    this.deliveries = deliveries;
    this.urls = urls;
    this.json = json;
    this.props = props;
    this.clock = clock;
  }

  @EventListener(ApplicationReadyEvent.class)
  @Transactional(readOnly = true)
  public void load() {
    if (!props.enabled()) {
      return;
    }
    reloadAccepted();
    FederationRelaySettingEntity row = relays.setting();
    setting = new Setting(row.getMode(), row.languageList());
  }

  public boolean enabled() {
    return props.enabled();
  }

  public boolean isAccepted(String actorUri) {
    return props.enabled() && actorUri != null && accepted.contains(actorUri);
  }

  public Setting setting() {
    return setting;
  }

  @Transactional(readOnly = true)
  public List<View> list() {
    return relays.list().stream().map(View::of).toList();
  }

  @Transactional
  public View subscribe(String raw) {
    if (!props.enabled()) {
      throw new FederationException(FederationErrorCode.RELAY_DISABLED);
    }
    String asked = address(raw);
    RemoteActorEntity actor =
        resolver
            .byActorUri(asked)
            .filter(found -> !found.onSuspendedServer())
            .orElseThrow(
                () -> new FederationException(FederationErrorCode.REMOTE_ACCOUNT_NOT_FOUND, asked));
    Optional<FederationRelayEntity> existing = relays.findByActorUri(actor.getActorUri());
    if (existing.isPresent()) {
      return View.of(existing.get());
    }
    String followId = urls.relayFollow(UUID.randomUUID().toString());
    FederationRelayEntity row =
        relays.save(
            new FederationRelayEntity(
                actor.getActorUri(), actor.getInbox(), actor.getDisplayName(), followId));
    deliveries.enqueue(
        null,
        followId,
        json.writeValueAsString(activity(follow(followId))),
        List.of(row.getInbox()));
    return View.of(row);
  }

  @Transactional
  public void unsubscribe(Long id) {
    FederationRelayEntity row =
        relays
            .findById(id)
            .orElseThrow(() -> new FederationException(FederationErrorCode.RELAY_NOT_FOUND));
    Map<String, Object> undo = new LinkedHashMap<>();
    undo.put("id", row.getFollowActivityId() + "/undo");
    undo.put("type", "Undo");
    undo.put("actor", urls.instance());
    undo.put("object", follow(row.getFollowActivityId()));
    deliveries.enqueue(
        null,
        row.getFollowActivityId() + "/undo",
        json.writeValueAsString(activity(undo)),
        List.of(row.getInbox()));
    relays.delete(row);
    reloadAccepted();
  }

  @Transactional
  public boolean answered(RemoteActorEntity actor, String followId, boolean accepted) {
    Optional<FederationRelayEntity> row =
        followId == null
            ? relays.findByActorUri(actor.getActorUri())
            : relays.findByFollowActivityId(followId);
    if (row.isEmpty() || !row.get().getActorUri().equals(actor.getActorUri())) {
      return false;
    }
    row.get().answered(accepted, clock.instant());
    relays.save(row.get());
    reloadAccepted();
    return true;
  }

  @Transactional
  public Setting change(RelayMode mode, List<String> languages) {
    FederationRelaySettingEntity row = relays.setting();
    row.change(mode, languages);
    relays.save(row);
    setting = new Setting(mode, List.copyOf(languages));
    return setting;
  }

  private void reloadAccepted() {
    accepted =
        relays.list().stream()
            .filter(row -> row.getState() == RelayState.ACCEPTED)
            .map(FederationRelayEntity::getActorUri)
            .collect(Collectors.toUnmodifiableSet());
  }

  private String address(String raw) {
    String asked = raw == null ? "" : raw.strip();
    try {
      URI uri = URI.create(asked);
      if ("https".equalsIgnoreCase(uri.getScheme())
          && uri.getHost() != null
          && !urls.isOurs(asked)) {
        return asked;
      }
    } catch (IllegalArgumentException malformed) {
      throw new FederationException(FederationErrorCode.RELAY_INVALID);
    }
    throw new FederationException(FederationErrorCode.RELAY_INVALID);
  }

  private Map<String, Object> follow(String followId) {
    Map<String, Object> follow = new LinkedHashMap<>();
    follow.put("id", followId);
    follow.put("type", "Follow");
    follow.put("actor", urls.instance());
    follow.put("object", ActivityStreams.PUBLIC);
    return follow;
  }

  private static Map<String, Object> activity(Map<String, Object> body) {
    Map<String, Object> activity = new LinkedHashMap<>();
    activity.put("@context", ActivityStreams.CONTEXT);
    activity.putAll(body);
    return activity;
  }
}
