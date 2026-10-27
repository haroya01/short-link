package com.example.short_link.federation.application;

import static com.example.short_link.federation.application.ActivityStreams.idOf;
import static com.example.short_link.federation.application.ActivityStreams.text;

import com.example.short_link.common.federation.RemoteHistory;
import com.example.short_link.common.note.RemoteNotes;
import com.example.short_link.federation.application.signature.Signer;
import com.example.short_link.federation.application.signature.SigningKeys;
import com.example.short_link.federation.domain.RemoteAccountStanding;
import com.example.short_link.federation.domain.RemoteActorEntity;
import com.example.short_link.federation.domain.repository.RemoteAccountStandingReader;
import com.example.short_link.federation.domain.repository.RemoteActorRepository;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// Delivery only brings what an account writes after someone here follows it, so an account seen
// for the first time has an empty page. Its outbox's first page fills that in: up to twenty of its
// own public notes, told to no one. An account with five such notes kept is left alone, and any
// account is looked at once in six hours, whoever asks.
@Slf4j
@Service
public class OutboxBackfill implements RemoteHistory {

  static final int FEW = 5;
  static final int MAX_NOTES = 20;
  static final Duration AGAIN_AFTER = Duration.ofHours(6);
  static final Duration WAIT = Duration.ofSeconds(10);

  private final RemoteActorRepository actors;
  private final RemoteAccountStandingReader standings;
  private final RemoteNotes remoteNotes;
  private final RemoteNoteIntake intake;
  private final FederationHttp http;
  private final SigningKeys signingKeys;
  private final JsonMapper json;
  private final Executor executor;
  private final Clock clock;
  private final Duration wait;

  @Autowired
  public OutboxBackfill(
      RemoteActorRepository actors,
      RemoteAccountStandingReader standings,
      RemoteNotes remoteNotes,
      RemoteNoteIntake intake,
      FederationHttp http,
      SigningKeys signingKeys,
      JsonMapper json,
      @Qualifier("taskExecutor") Executor executor,
      Clock clock) {
    this(actors, standings, remoteNotes, intake, http, signingKeys, json, executor, clock, WAIT);
  }

  OutboxBackfill(
      RemoteActorRepository actors,
      RemoteAccountStandingReader standings,
      RemoteNotes remoteNotes,
      RemoteNoteIntake intake,
      FederationHttp http,
      SigningKeys signingKeys,
      JsonMapper json,
      Executor executor,
      Clock clock,
      Duration wait) {
    this.actors = actors;
    this.standings = standings;
    this.remoteNotes = remoteNotes;
    this.intake = intake;
    this.http = http;
    this.signingKeys = signingKeys;
    this.json = json;
    this.executor = executor;
    this.clock = clock;
    this.wait = wait;
  }

  // A member opening the account's notes waits for the fill as long as one remote read may take,
  // then sees what is kept by then; a slow fill finishes on its own and shows on the next load.
  @Override
  public void fillIn(Long remoteActorId, Long viewerId) {
    if (viewerId == null) {
      return;
    }
    Optional<RemoteActorEntity> due =
        due(remoteActorId).filter(actor -> hears(viewerId, actor)).filter(this::claim);
    if (due.isEmpty()) {
      return;
    }
    try {
      CompletableFuture.runAsync(() -> fill(due.get()), executor)
          .get(wait.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException slow) {
      log.info("federation outbox fill outlived the wait actor={}", remoteActorId);
    } catch (ExecutionException failed) {
      log.warn("federation outbox fill failed actor={}", remoteActorId, failed.getCause());
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
    }
  }

  public void followed(Long remoteActorId) {
    due(remoteActorId).filter(this::claim).ifPresent(this::fill);
  }

  private Optional<RemoteActorEntity> due(Long remoteActorId) {
    Instant lookedAtBefore = clock.instant().minus(AGAIN_AFTER);
    return actors
        .findById(remoteActorId)
        .filter(actor -> !actor.onSuspendedServer())
        .filter(
            actor ->
                actor.getOutboxCheckedAt() == null
                    || actor.getOutboxCheckedAt().isBefore(lookedAtBefore));
  }

  // Someone who blocked the account or its server sees none of it, so nothing is fetched for them.
  private boolean hears(Long viewerId, RemoteActorEntity actor) {
    RemoteAccountStanding standing =
        standings
            .of(viewerId, List.of(actor.getId()), clock.instant())
            .getOrDefault(actor.getId(), RemoteAccountStanding.NONE);
    return !standing.blocking() && !standing.domainBlocked();
  }

  private boolean claim(RemoteActorEntity actor) {
    Instant now = clock.instant();
    return actors.claimOutbox(actor.getId(), now, now.minus(AGAIN_AFTER))
        && remoteNotes.countListed(actor.getId(), FEW) < FEW;
  }

  void fill(RemoteActorEntity actor) {
    Signer signer = signingKeys.forInstance();
    Optional<JsonNode> outbox =
        fetch(actor, actor.getActorUri(), signer)
            .flatMap(document -> fetch(actor, idOf(document.get("outbox")), signer));
    if (outbox.isEmpty()) {
      return;
    }
    List<JsonNode> items = items(outbox.get());
    if (items.isEmpty()) {
      JsonNode first = outbox.get().get("first");
      items =
          first != null && first.isObject()
              ? items(first)
              : fetch(actor, idOf(first), signer).map(OutboxBackfill::items).orElse(List.of());
    }
    int kept = 0;
    for (JsonNode item : items) {
      if (kept == MAX_NOTES) {
        break;
      }
      JsonNode object = "Create".equals(text(item.get("type"))) ? item.get("object") : item;
      if (object != null && object.isObject() && intake.fromHistory(actor, object)) {
        kept++;
      }
    }
    log.info("federation outbox fill actor={} kept={}", actor.getId(), kept);
  }

  private static List<JsonNode> items(JsonNode collection) {
    JsonNode items = collection.get("orderedItems");
    if (items == null) {
      items = collection.get("items");
    }
    List<JsonNode> listed = new ArrayList<>();
    if (items != null && items.isArray()) {
      items.forEach(listed::add);
    }
    return listed;
  }

  // Only the account's own server is asked, so a document cannot send the fetch elsewhere.
  private Optional<JsonNode> fetch(RemoteActorEntity actor, String address, Signer signer) {
    if (address == null || !actor.getDomain().equals(RemoteActorParser.host(address))) {
      return Optional.empty();
    }
    FederationHttp.Result result = http.get(URI.create(address), signer);
    if (!(result instanceof FederationHttp.Result.Ok ok) || !ok.activityStreams()) {
      log.info("federation outbox fetch outcome={} uri={}", result, address);
      return Optional.empty();
    }
    try {
      JsonNode document = json.readTree(ok.body());
      return document != null && document.isObject() ? Optional.of(document) : Optional.empty();
    } catch (JacksonException malformed) {
      return Optional.empty();
    }
  }
}
