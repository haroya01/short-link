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
import com.example.short_link.federation.domain.repository.FederationRelayedNoteRepository;
import com.example.short_link.federation.exception.FederationErrorCode;
import com.example.short_link.federation.exception.FederationException;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

// The instance actor follows a relay with the Public collection as the object, as Mastodon does.
// Which relays have accepted, and the one setting, are held in memory so an announce costs no query
// to recognise.
@Slf4j
@Service
public class RelaySubscriptions {

  private final FederationRelayRepository relays;
  private final FederationRelayedNoteRepository relayed;
  private final RemoteActorResolver resolver;
  private final DeliveryQueue deliveries;
  private final FederationUrls urls;
  private final JsonMapper json;
  private final RelayProperties props;
  private final Clock clock;

  private volatile Map<String, Long> accepted = Map.of();
  private volatile Setting setting = new Setting(RelayMode.OFF, List.of());

  public record Setting(RelayMode mode, List<String> languages) {}

  public record Listing(List<View> relays, long kept) {}

  public record View(
      Long id,
      String actorUri,
      String label,
      RelayState state,
      Instant acceptedAt,
      Instant createdAt,
      long kept) {

    static View of(FederationRelayEntity row, long kept) {
      return new View(
          row.getId(),
          row.getActorUri(),
          row.getLabel(),
          row.getState(),
          row.getAcceptedAt(),
          row.getCreatedAt(),
          kept);
    }
  }

  @Autowired
  public RelaySubscriptions(
      FederationRelayRepository relays,
      FederationRelayedNoteRepository relayed,
      RemoteActorResolver resolver,
      DeliveryQueue deliveries,
      FederationUrls urls,
      JsonMapper json,
      RelayProperties props) {
    this(relays, relayed, resolver, deliveries, urls, json, props, Clock.systemUTC());
  }

  RelaySubscriptions(
      FederationRelayRepository relays,
      FederationRelayedNoteRepository relayed,
      RemoteActorResolver resolver,
      DeliveryQueue deliveries,
      FederationUrls urls,
      JsonMapper json,
      RelayProperties props,
      Clock clock) {
    this.relays = relays;
    this.relayed = relayed;
    this.resolver = resolver;
    this.deliveries = deliveries;
    this.urls = urls;
    this.json = json;
    this.props = props;
    this.clock = clock;
  }

  @EventListener(ApplicationReadyEvent.class)
  @Transactional
  public void load() {
    if (!props.enabled()) {
      return;
    }
    reloadAccepted();
    FederationRelaySettingEntity row = relays.setting();
    if (row.startsIn(props.mode())) {
      relays.save(row);
    }
    setting = new Setting(row.getMode(), row.languageList());
    log.info(
        "federation relay mode={} chosen={} environment={}",
        row.getMode(),
        row.getChosenAt() != null,
        props.mode());
  }

  public boolean enabled() {
    return props.enabled();
  }

  public boolean isAccepted(String actorUri) {
    return props.enabled() && actorUri != null && accepted.containsKey(actorUri);
  }

  public Long relayId(String actorUri) {
    return actorUri == null ? null : accepted.get(actorUri);
  }

  public Setting setting() {
    return setting;
  }

  @Transactional(readOnly = true)
  public Listing list() {
    Map<Long, Long> kept = relayed.countByRelay();
    return new Listing(
        relays.list().stream()
            .map(row -> View.of(row, kept.getOrDefault(row.getId(), 0L)))
            .toList(),
        kept.values().stream().mapToLong(Long::longValue).sum());
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
      return View.of(existing.get(), 0);
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
    return View.of(row, 0);
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
        Optional.ofNullable(followId)
            .flatMap(relays::findByFollowActivityId)
            .or(() -> relays.findByActorUri(actor.getActorUri()));
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
    row.change(mode, languages, clock.instant());
    relays.save(row);
    setting = new Setting(mode, List.copyOf(languages));
    return setting;
  }

  // Kept in the database, so storing does not come back with the next start.
  @Transactional
  public void stopStoring() {
    Setting current = setting;
    if (current.mode() == RelayMode.STORE) {
      change(RelayMode.COUNT, current.languages());
    }
  }

  private void reloadAccepted() {
    accepted =
        relays.list().stream()
            .filter(row -> row.getState() == RelayState.ACCEPTED)
            .collect(
                Collectors.toUnmodifiableMap(
                    FederationRelayEntity::getActorUri, FederationRelayEntity::getId));
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
