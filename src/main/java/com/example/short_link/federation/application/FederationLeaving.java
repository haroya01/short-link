package com.example.short_link.federation.application;

import com.example.short_link.federation.application.delivery.DeliveryQueue;
import com.example.short_link.federation.domain.FederationActorEntity;
import com.example.short_link.federation.domain.repository.FederationActorRepository;
import com.example.short_link.federation.domain.repository.FederationFollowerRepository;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

// Leaving the fediverse (turning federation off, or deleting the account) unfollows everyone it
// followed elsewhere, tells every follower's server to drop the account and its notes, then
// forgets the followers. Remote copies are deleted
// by those servers, not by us; re-enabling starts with no followers.
@Service
public class FederationLeaving {

  private final FederationActorRepository actors;
  private final FederationFollowerRepository followers;
  private final RemoteFollowing following;
  private final DeliveryQueue deliveries;
  private final FederationUrls urls;
  private final JsonMapper json;
  private final Clock clock;

  @Autowired
  public FederationLeaving(
      FederationActorRepository actors,
      FederationFollowerRepository followers,
      RemoteFollowing following,
      DeliveryQueue deliveries,
      FederationUrls urls,
      JsonMapper json) {
    this(actors, followers, following, deliveries, urls, json, Clock.systemUTC());
  }

  FederationLeaving(
      FederationActorRepository actors,
      FederationFollowerRepository followers,
      RemoteFollowing following,
      DeliveryQueue deliveries,
      FederationUrls urls,
      JsonMapper json,
      Clock clock) {
    this.actors = actors;
    this.followers = followers;
    this.following = following;
    this.deliveries = deliveries;
    this.urls = urls;
    this.json = json;
    this.clock = clock;
  }

  @Transactional
  public void leave(Long userId) {
    Optional<FederationActorEntity> actor = actors.findByUserId(userId);
    if (actor.isEmpty()) {
      return;
    }
    String actorUri = urls.actor(actor.get().getPublicId());
    following.leave(userId, actorUri);
    List<String> inboxes = followers.deliveryInboxes(userId);
    if (!inboxes.isEmpty()) {
      String id = actorUri + "#delete/" + clock.millis();
      Map<String, Object> delete = new LinkedHashMap<>();
      delete.put("@context", ActivityStreams.CONTEXT);
      delete.put("id", id);
      delete.put("type", "Delete");
      delete.put("actor", actorUri);
      delete.put("object", actorUri);
      delete.put("to", List.of(ActivityStreams.PUBLIC));
      deliveries.enqueue(userId, id, json.writeValueAsString(delete), inboxes);
    }
    followers.deleteAllForUser(userId);
  }
}
