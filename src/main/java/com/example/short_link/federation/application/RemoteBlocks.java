package com.example.short_link.federation.application;

import com.example.short_link.common.event.RemoteAccountBlockedEvent;
import com.example.short_link.federation.application.delivery.DeliveryQueue;
import com.example.short_link.federation.domain.OnRemoteAccount;
import com.example.short_link.federation.domain.RemoteAccountStanding;
import com.example.short_link.federation.domain.RemoteActorEntity;
import com.example.short_link.federation.domain.RemoteBlockEntity;
import com.example.short_link.federation.domain.repository.FederationActorRepository;
import com.example.short_link.federation.domain.repository.RemoteAccountStandingReader;
import com.example.short_link.federation.domain.repository.RemoteBlockRepository;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class RemoteBlocks {

  private static final RemoteAccountStanding BLOCKED =
      RemoteAccountStanding.NONE.withBlocking(true);

  private final RemoteBlockRepository blocks;
  private final RemoteAccountStandingReader standings;
  private final RemoteFollowing following;
  private final FederationFollowers followers;
  private final FederationActorRepository actorRows;
  private final DeliveryQueue deliveries;
  private final FederationUrls urls;
  private final JsonMapper json;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  @Transactional
  public RemoteAccountView block(Long userId, Long remoteActorId) {
    RemoteActorEntity remote = following.remote(remoteActorId);
    if (blocks.find(userId, remote.getId()).isEmpty()) {
      Optional<String> actor = actor(userId);
      following.stopFollowing(userId, actor, remote);
      followers.drop(userId, actor, remote);
      String blockId = actor.map(uri -> uri + "#blocks/" + UUID.randomUUID()).orElse(null);
      blocks.save(new RemoteBlockEntity(userId, remote.getId(), blockId));
      actor.ifPresent(uri -> send(userId, blockId, activity(blockId, uri, remote), remote));
      events.publishEvent(new RemoteAccountBlockedEvent(userId, remote.getId()));
    }
    return RemoteAccountView.of(
        remote, Optional.empty(), following.standing(userId, remote).withBlocking(true));
  }

  @Transactional
  public RemoteAccountView unblock(Long userId, Long remoteActorId) {
    RemoteActorEntity remote = following.known(remoteActorId);
    Optional<RemoteBlockEntity> row = blocks.find(userId, remote.getId());
    if (row.isPresent()) {
      blocks.delete(row.get());
      String blockId = row.get().getActivityId();
      if (blockId != null) {
        actor(userId).ifPresent(uri -> undo(userId, uri, blockId, remote));
      }
    }
    return following.view(userId, remote, following.standing(userId, remote).withBlocking(false));
  }

  @Transactional(readOnly = true)
  public List<RemoteAccountView> blocks(Long userId, int page, int size) {
    List<OnRemoteAccount<RemoteBlockEntity>> rows = blocks.page(userId, page * size, size);
    Map<Long, RemoteAccountStanding> marks =
        standings.of(
            userId, rows.stream().map(row -> row.actor().getId()).toList(), clock.instant());
    return rows.stream()
        .map(
            row ->
                RemoteAccountView.of(
                    row.actor(),
                    Optional.empty(),
                    marks.getOrDefault(row.actor().getId(), BLOCKED)))
        .toList();
  }

  private Optional<String> actor(Long userId) {
    return actorRows.findByUserId(userId).map(row -> urls.actor(row.getPublicId()));
  }

  private void undo(Long userId, String actor, String blockId, RemoteActorEntity remote) {
    String undoId = actor + "#undo/" + UUID.randomUUID();
    Map<String, Object> undo = new LinkedHashMap<>();
    undo.put("id", undoId);
    undo.put("type", "Undo");
    undo.put("actor", actor);
    undo.put("object", activity(blockId, actor, remote));
    send(userId, undoId, undo, remote);
  }

  private void send(
      Long userId, String id, Map<String, Object> activity, RemoteActorEntity remote) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("@context", ActivityStreams.CONTEXT);
    body.putAll(activity);
    deliveries.enqueue(userId, id, json.writeValueAsString(body), List.of(remote.getInbox()));
  }

  private static Map<String, Object> activity(
      String blockId, String actor, RemoteActorEntity remote) {
    Map<String, Object> block = new LinkedHashMap<>();
    block.put("id", blockId);
    block.put("type", "Block");
    block.put("actor", actor);
    block.put("object", remote.getActorUri());
    return block;
  }
}
