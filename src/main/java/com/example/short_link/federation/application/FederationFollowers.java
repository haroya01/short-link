package com.example.short_link.federation.application;

import com.example.short_link.common.event.RemoteFollowedEvent;
import com.example.short_link.federation.application.delivery.DeliveryQueue;
import com.example.short_link.federation.domain.FederationFollowerEntity;
import com.example.short_link.federation.domain.FollowOnDomain;
import com.example.short_link.federation.domain.RemoteActorEntity;
import com.example.short_link.federation.domain.repository.FederationActorRepository;
import com.example.short_link.federation.domain.repository.FederationFollowerRepository;
import com.example.short_link.federation.domain.repository.RemoteActorRepository;
import com.example.short_link.federation.domain.repository.UserDomainBlockRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
  private final UserDomainBlockRepository domainBlocks;
  private final FederationActorRepository actorRows;
  private final RemoteActorRepository remoteActors;
  private final DeliveryQueue deliveries;
  private final FederationUrls urls;
  private final JsonMapper json;
  private final ApplicationEventPublisher events;

  @Transactional
  public void follow(LocalActor target, RemoteActorEntity follower, String followId) {
    Long userId = target.user().id();
    String actor = urls.actor(target.publicId());
    if (domainBlocks.blocks(userId, follower.getDomain())) {
      answer("Reject", userId, actor, followId, follower);
      return;
    }
    Optional<FederationFollowerEntity> existing = followers.find(userId, follower.getId());
    FederationFollowerEntity row =
        existing.orElseGet(() -> new FederationFollowerEntity(userId, follower.getId(), followId));
    row.refollow(followId);
    followers.save(row);
    if (existing.isEmpty()) {
      events.publishEvent(new RemoteFollowedEvent(userId, follower.getId()));
    }
    answer("Accept", userId, actor, followId, follower);
  }

  // Blocking a server rejects every follow from it, so those servers drop the follow too, as on
  // Mastodon.
  @Transactional
  public void rejectDomain(Long userId, String domain) {
    List<FollowOnDomain<FederationFollowerEntity>> rows = followers.onDomain(userId, domain);
    if (rows.isEmpty()) {
      return;
    }
    Optional<String> actor =
        actorRows.findByUserId(userId).map(row -> urls.actor(row.getPublicId()));
    for (FollowOnDomain<FederationFollowerEntity> row : rows) {
      followers.delete(userId, row.actor().getId());
      actor.ifPresent(
          uri -> answer("Reject", userId, uri, row.follow().getFollowActivityId(), row.actor()));
    }
  }

  private void answer(
      String type, Long userId, String actor, String followId, RemoteActorEntity follower) {
    String answerId =
        actor + "#" + type.toLowerCase(Locale.ROOT) + "s/follows/" + UUID.randomUUID();
    Map<String, Object> follow = new LinkedHashMap<>();
    follow.put("id", followId);
    follow.put("type", "Follow");
    follow.put("actor", follower.getActorUri());
    follow.put("object", actor);
    Map<String, Object> answer = new LinkedHashMap<>();
    answer.put("@context", ActivityStreams.CONTEXT);
    answer.put("id", answerId);
    answer.put("type", type);
    answer.put("actor", actor);
    answer.put("object", follow);
    deliveries.enqueue(
        userId, answerId, json.writeValueAsString(answer), List.of(follower.getInbox()));
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
