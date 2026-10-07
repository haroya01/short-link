package com.example.short_link.federation.application;

import com.example.short_link.federation.domain.FederationFollowerEntity;
import com.example.short_link.federation.domain.FollowOnDomain;
import com.example.short_link.federation.domain.RemoteActorEntity;
import java.time.Instant;

public record RemoteFollowRequestView(
    Long id,
    String acct,
    String username,
    String domain,
    String displayName,
    String avatarUrl,
    String url,
    Instant requestedAt) {

  static RemoteFollowRequestView of(FollowOnDomain<FederationFollowerEntity> row) {
    RemoteActorEntity actor = row.actor();
    String username = actor.getUsername() == null ? "" : actor.getUsername();
    return new RemoteFollowRequestView(
        actor.getId(),
        username + "@" + actor.getDomain(),
        username,
        actor.getDomain(),
        actor.getDisplayName(),
        actor.getAvatarUrl(),
        actor.getProfileUrl() == null ? actor.getActorUri() : actor.getProfileUrl(),
        row.follow().getCreatedAt());
  }
}
