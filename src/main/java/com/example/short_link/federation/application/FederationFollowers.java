package com.example.short_link.federation.application;

import com.example.short_link.common.event.FollowRequestSettledEvent;
import com.example.short_link.common.event.FollowRequestedEvent;
import com.example.short_link.common.event.RemoteFollowedEvent;
import com.example.short_link.federation.application.delivery.DeliveryQueue;
import com.example.short_link.federation.domain.FederationFollowerEntity;
import com.example.short_link.federation.domain.FollowOnDomain;
import com.example.short_link.federation.domain.RemoteActorEntity;
import com.example.short_link.federation.domain.repository.FederationActorRepository;
import com.example.short_link.federation.domain.repository.FederationFollowerRepository;
import com.example.short_link.federation.domain.repository.RemoteActorRepository;
import com.example.short_link.federation.domain.repository.UserDomainBlockRepository;
import com.example.short_link.federation.exception.FederationErrorCode;
import com.example.short_link.federation.exception.FederationException;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

// Follows are accepted at once unless the member locked the account (the actor document says
// manuallyApprovesFollowers): then the Follow waits, unanswered, until the member approves (Accept)
// or turns it down (Reject), as on Mastodon. A repeated Follow of an accepted follower gets a
// fresh Accept: the remote re-sends Follow when it never saw our Accept.
@Service
public class FederationFollowers {

  static final int REQUESTS_PAGE_SIZE = 40;

  private final FederationFollowerRepository followers;
  private final UserDomainBlockRepository domainBlocks;
  private final FederationActorRepository actorRows;
  private final RemoteActorRepository remoteActors;
  private final DeliveryQueue deliveries;
  private final FederationUrls urls;
  private final JsonMapper json;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  @Autowired
  public FederationFollowers(
      FederationFollowerRepository followers,
      UserDomainBlockRepository domainBlocks,
      FederationActorRepository actorRows,
      RemoteActorRepository remoteActors,
      DeliveryQueue deliveries,
      FederationUrls urls,
      JsonMapper json,
      ApplicationEventPublisher events) {
    this(
        followers,
        domainBlocks,
        actorRows,
        remoteActors,
        deliveries,
        urls,
        json,
        events,
        Clock.systemUTC());
  }

  FederationFollowers(
      FederationFollowerRepository followers,
      UserDomainBlockRepository domainBlocks,
      FederationActorRepository actorRows,
      RemoteActorRepository remoteActors,
      DeliveryQueue deliveries,
      FederationUrls urls,
      JsonMapper json,
      ApplicationEventPublisher events,
      Clock clock) {
    this.followers = followers;
    this.domainBlocks = domainBlocks;
    this.actorRows = actorRows;
    this.remoteActors = remoteActors;
    this.deliveries = deliveries;
    this.urls = urls;
    this.json = json;
    this.events = events;
    this.clock = clock;
  }

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
    boolean waits = target.user().locked() && row.isPending();
    if (!waits && row.isPending()) {
      row.accept(clock.instant());
    }
    followers.save(row);
    if (existing.isEmpty()) {
      events.publishEvent(
          waits
              ? new FollowRequestedEvent(userId, null, follower.getId())
              : new RemoteFollowedEvent(userId, follower.getId()));
    }
    if (!waits) {
      answer("Accept", userId, actor, followId, follower);
    }
  }

  @Transactional(readOnly = true)
  public boolean accepts(Long userId, Long remoteActorId) {
    return followers.find(userId, remoteActorId).filter(row -> !row.isPending()).isPresent();
  }

  @Transactional(readOnly = true)
  public List<RemoteFollowRequestView> pending(Long userId, int page) {
    return followers.pending(userId, Math.max(page, 0), REQUESTS_PAGE_SIZE).stream()
        .map(RemoteFollowRequestView::of)
        .toList();
  }

  @Transactional
  public void authorize(Long userId, Long remoteActorId) {
    FederationFollowerEntity row = waiting(userId, remoteActorId);
    row.accept(clock.instant());
    followers.save(row);
    events.publishEvent(new FollowRequestSettledEvent(userId, null, remoteActorId));
    answerAs(userId, "Accept", row.getFollowActivityId(), remoteActorId);
  }

  @Transactional
  public void reject(Long userId, Long remoteActorId) {
    FederationFollowerEntity row = waiting(userId, remoteActorId);
    followers.delete(userId, remoteActorId);
    events.publishEvent(new FollowRequestSettledEvent(userId, null, remoteActorId));
    answerAs(userId, "Reject", row.getFollowActivityId(), remoteActorId);
  }

  // Unlocking the account lets everyone from elsewhere who was waiting in.
  @Transactional
  public void acceptAll(Long userId) {
    List<FollowOnDomain<FederationFollowerEntity>> rows = followers.pending(userId);
    if (rows.isEmpty()) {
      return;
    }
    Optional<String> actor =
        actorRows.findByUserId(userId).map(row -> urls.actor(row.getPublicId()));
    for (FollowOnDomain<FederationFollowerEntity> row : rows) {
      row.follow().accept(clock.instant());
      followers.save(row.follow());
      actor.ifPresent(
          uri -> answer("Accept", userId, uri, row.follow().getFollowActivityId(), row.actor()));
    }
  }

  private FederationFollowerEntity waiting(Long userId, Long remoteActorId) {
    return followers
        .find(userId, remoteActorId)
        .filter(FederationFollowerEntity::isPending)
        .orElseThrow(
            () -> new FederationException(FederationErrorCode.REMOTE_FOLLOW_REQUEST_NOT_FOUND));
  }

  private void answerAs(Long userId, String type, String followId, Long remoteActorId) {
    Optional<String> actor =
        actorRows.findByUserId(userId).map(row -> urls.actor(row.getPublicId()));
    Optional<RemoteActorEntity> follower = remoteActors.findById(remoteActorId);
    if (actor.isPresent() && follower.isPresent()) {
      answer(type, userId, actor.get(), followId, follower.get());
    }
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
