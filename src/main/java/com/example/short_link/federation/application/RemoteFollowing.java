package com.example.short_link.federation.application;

import com.example.short_link.federation.application.delivery.DeliveryQueue;
import com.example.short_link.federation.domain.FederationActorEntity;
import com.example.short_link.federation.domain.FederationFollowingEntity;
import com.example.short_link.federation.domain.FollowOnDomain;
import com.example.short_link.federation.domain.RemoteActorEntity;
import com.example.short_link.federation.domain.repository.FederationActorRepository;
import com.example.short_link.federation.domain.repository.FederationFollowingRepository;
import com.example.short_link.federation.domain.repository.RemoteActorRepository;
import com.example.short_link.federation.domain.repository.UserDomainBlockRepository;
import com.example.short_link.federation.exception.FederationErrorCode;
import com.example.short_link.federation.exception.FederationException;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

// Following someone on another server is a request until their server sends Accept, as on
// Mastodon; a locked account may never answer, and the row stays a request.
@Service
public class RemoteFollowing {

  private final FederationFollowingRepository followings;
  private final UserDomainBlockRepository domainBlocks;
  private final RemoteActorRepository remoteActors;
  private final RemoteAccountFinder finder;
  private final FederationActorService localActors;
  private final FederationActorRepository actorRows;
  private final DeliveryQueue deliveries;
  private final FederationUrls urls;
  private final JsonMapper json;
  private final Clock clock;

  @Autowired
  public RemoteFollowing(
      FederationFollowingRepository followings,
      UserDomainBlockRepository domainBlocks,
      RemoteActorRepository remoteActors,
      RemoteAccountFinder finder,
      FederationActorService localActors,
      FederationActorRepository actorRows,
      DeliveryQueue deliveries,
      FederationUrls urls,
      JsonMapper json) {
    this(
        followings,
        domainBlocks,
        remoteActors,
        finder,
        localActors,
        actorRows,
        deliveries,
        urls,
        json,
        Clock.systemUTC());
  }

  RemoteFollowing(
      FederationFollowingRepository followings,
      UserDomainBlockRepository domainBlocks,
      RemoteActorRepository remoteActors,
      RemoteAccountFinder finder,
      FederationActorService localActors,
      FederationActorRepository actorRows,
      DeliveryQueue deliveries,
      FederationUrls urls,
      JsonMapper json,
      Clock clock) {
    this.followings = followings;
    this.domainBlocks = domainBlocks;
    this.remoteActors = remoteActors;
    this.finder = finder;
    this.localActors = localActors;
    this.actorRows = actorRows;
    this.deliveries = deliveries;
    this.urls = urls;
    this.json = json;
    this.clock = clock;
  }

  @Transactional
  public RemoteAccountView lookup(Long userId, String handle) {
    RemoteActorEntity actor =
        finder
            .find(handle)
            .filter(found -> !found.onSuspendedServer())
            .orElseThrow(
                () ->
                    new FederationException(
                        FederationErrorCode.REMOTE_ACCOUNT_NOT_FOUND, handle.strip()));
    return view(userId, actor);
  }

  @Transactional(readOnly = true)
  public RemoteAccountView account(Long userId, Long remoteActorId) {
    return view(userId, remote(remoteActorId));
  }

  private RemoteAccountView view(Long userId, RemoteActorEntity actor) {
    return RemoteAccountView.of(
        actor,
        followings.find(userId, actor.getId()),
        domainBlocks.blocks(userId, actor.getDomain()));
  }

  @Transactional
  public RemoteAccountView follow(Long userId, Long remoteActorId) {
    RemoteActorEntity remote = remote(remoteActorId);
    if (domainBlocks.blocks(userId, remote.getDomain())) {
      throw new FederationException(FederationErrorCode.REMOTE_DOMAIN_BLOCKED, remote.getDomain());
    }
    LocalActor me =
        localActors
            .byUserId(userId)
            .orElseThrow(() -> new FederationException(FederationErrorCode.FEDERATION_DISABLED));
    Optional<FederationFollowingEntity> existing = followings.find(userId, remote.getId());
    if (existing.isPresent()) {
      return RemoteAccountView.of(remote, existing);
    }
    String actor = urls.actor(me.publicId());
    String followId = actor + "#follows/" + UUID.randomUUID();
    FederationFollowingEntity row =
        followings.save(new FederationFollowingEntity(userId, remote.getId(), followId));
    Map<String, Object> follow = new LinkedHashMap<>();
    follow.put("@context", ActivityStreams.CONTEXT);
    follow.putAll(follow(followId, actor, remote.getActorUri()));
    deliveries.enqueue(
        userId, followId, json.writeValueAsString(follow), List.of(remote.getInbox()));
    return RemoteAccountView.of(remote, Optional.of(row));
  }

  @Transactional
  public RemoteAccountView unfollow(Long userId, Long remoteActorId) {
    RemoteActorEntity remote = remote(remoteActorId);
    Optional<FederationFollowingEntity> existing = followings.find(userId, remote.getId());
    if (existing.isPresent()) {
      followings.delete(existing.get());
      actorRows
          .findByUserId(userId)
          .map(FederationActorEntity::getPublicId)
          .ifPresent(publicId -> undo(userId, urls.actor(publicId), existing.get(), remote));
    }
    return RemoteAccountView.of(remote, Optional.empty());
  }

  @Transactional(readOnly = true)
  public List<RemoteAccountView> following(Long userId, int page, int size) {
    List<FederationFollowingEntity> rows = followings.page(userId, page * size, size);
    Map<Long, RemoteActorEntity> actors =
        remoteActors
            .findAllById(rows.stream().map(FederationFollowingEntity::getRemoteActorId).toList())
            .stream()
            .collect(Collectors.toMap(RemoteActorEntity::getId, Function.identity()));
    return rows.stream()
        .filter(row -> actors.containsKey(row.getRemoteActorId()))
        .map(row -> RemoteAccountView.of(actors.get(row.getRemoteActorId()), Optional.of(row)))
        .toList();
  }

  // Mastodon echoes our Follow inside Accept and Reject; servers that send only its id, or a
  // Follow with an id of their own, are matched by who followed whom.
  @Transactional
  public boolean answered(
      RemoteActorEntity remote, String followId, String localActorUri, boolean accepted) {
    Optional<FederationFollowingEntity> row =
        followId == null
            ? Optional.empty()
            : followings.findByFollowActivity(remote.getId(), followId);
    if (row.isEmpty() && localActorUri != null) {
      row =
          urls.publicIdOf(localActorUri)
              .flatMap(actorRows::findByPublicId)
              .flatMap(actor -> followings.find(actor.getUserId(), remote.getId()));
    }
    if (row.isEmpty()) {
      return false;
    }
    if (accepted) {
      row.get().accept(clock.instant());
      followings.save(row.get());
    } else {
      followings.delete(row.get());
    }
    return true;
  }

  // Blocking a server ends every follow of its accounts, each with an Undo, as on Mastodon.
  @Transactional
  public void leaveDomain(Long userId, String domain) {
    List<FollowOnDomain<FederationFollowingEntity>> rows = followings.onDomain(userId, domain);
    if (rows.isEmpty()) {
      return;
    }
    Optional<String> actor =
        actorRows.findByUserId(userId).map(row -> urls.actor(row.getPublicId()));
    for (FollowOnDomain<FederationFollowingEntity> row : rows) {
      followings.delete(row.follow());
      actor.ifPresent(uri -> undo(userId, uri, row.follow(), row.actor()));
    }
  }

  @Transactional
  public void leave(Long userId, String actorUri) {
    List<FederationFollowingEntity> rows = followings.allForUser(userId);
    if (rows.isEmpty()) {
      return;
    }
    Map<Long, RemoteActorEntity> actors =
        remoteActors
            .findAllById(rows.stream().map(FederationFollowingEntity::getRemoteActorId).toList())
            .stream()
            .collect(Collectors.toMap(RemoteActorEntity::getId, Function.identity()));
    for (FederationFollowingEntity row : rows) {
      RemoteActorEntity remote = actors.get(row.getRemoteActorId());
      if (remote != null) {
        undo(userId, actorUri, row, remote);
      }
    }
    followings.deleteAllForUser(userId);
  }

  private void undo(
      Long userId, String actor, FederationFollowingEntity row, RemoteActorEntity remote) {
    String undoId = actor + "#undo/" + UUID.randomUUID();
    Map<String, Object> undo = new LinkedHashMap<>();
    undo.put("@context", ActivityStreams.CONTEXT);
    undo.put("id", undoId);
    undo.put("type", "Undo");
    undo.put("actor", actor);
    undo.put("object", follow(row.getFollowActivityId(), actor, remote.getActorUri()));
    deliveries.enqueue(userId, undoId, json.writeValueAsString(undo), List.of(remote.getInbox()));
  }

  private static Map<String, Object> follow(String id, String actor, String object) {
    Map<String, Object> follow = new LinkedHashMap<>();
    follow.put("id", id);
    follow.put("type", "Follow");
    follow.put("actor", actor);
    follow.put("object", object);
    return follow;
  }

  private RemoteActorEntity remote(Long remoteActorId) {
    return remoteActors
        .findById(remoteActorId)
        .filter(found -> !found.onSuspendedServer())
        .orElseThrow(
            () ->
                new FederationException(
                    FederationErrorCode.REMOTE_ACCOUNT_NOT_FOUND, remoteActorId));
  }
}
