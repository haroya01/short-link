package com.example.short_link.federation.application;

import com.example.short_link.common.event.RemoteFollowedEvent;
import com.example.short_link.federation.application.delivery.DeliveryQueue;
import com.example.short_link.federation.domain.FederationFollowerEntity;
import com.example.short_link.federation.domain.RemoteActorEntity;
import com.example.short_link.federation.domain.repository.FederationFollowerRepository;
import com.example.short_link.federation.domain.repository.RemoteActorRepository;
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

// Follows are accepted at once (the actor document says manuallyApprovesFollowers: false). A
// repeated Follow gets a fresh Accept: the remote re-sends Follow when it never saw our Accept.
@Service
@RequiredArgsConstructor
public class FederationFollowers {

  private final FederationFollowerRepository followers;
  private final RemoteActorRepository remoteActors;
  private final DeliveryQueue deliveries;
  private final FederationUrls urls;
  private final JsonMapper json;
  private final ApplicationEventPublisher events;

  @Transactional
  public void follow(LocalActor target, RemoteActorEntity follower, String followId) {
    Long userId = target.user().id();
    Optional<FederationFollowerEntity> existing = followers.find(userId, follower.getId());
    FederationFollowerEntity row =
        existing.orElseGet(() -> new FederationFollowerEntity(userId, follower.getId(), followId));
    row.refollow(followId);
    followers.save(row);
    if (existing.isEmpty()) {
      events.publishEvent(new RemoteFollowedEvent(userId, follower.getId()));
    }

    String actor = urls.actor(target.publicId());
    String acceptId = actor + "#accepts/follows/" + UUID.randomUUID();
    Map<String, Object> follow = new LinkedHashMap<>();
    follow.put("id", followId);
    follow.put("type", "Follow");
    follow.put("actor", follower.getActorUri());
    follow.put("object", actor);
    Map<String, Object> accept = new LinkedHashMap<>();
    accept.put("@context", ActivityStreams.CONTEXT);
    accept.put("id", acceptId);
    accept.put("type", "Accept");
    accept.put("actor", actor);
    accept.put("object", follow);
    deliveries.enqueue(
        userId, acceptId, json.writeValueAsString(accept), List.of(follower.getInbox()));
  }

  @Transactional
  public int unfollow(Long userId, RemoteActorEntity follower) {
    return followers.delete(userId, follower.getId());
  }

  @Transactional
  public int unfollow(RemoteActorEntity follower, String followId) {
    return followers.deleteByFollowActivity(follower.getId(), followId);
  }

  @Transactional
  public void forget(RemoteActorEntity actor) {
    remoteActors.delete(actor);
  }
}
