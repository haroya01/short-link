package com.example.short_link.federation.application;

import com.example.short_link.federation.domain.FederationFollowingEntity;
import com.example.short_link.federation.domain.RemoteAccountMatch;
import com.example.short_link.federation.domain.RemoteAccountStanding;
import com.example.short_link.federation.domain.RemoteActorEntity;
import java.util.Optional;

public record RemoteAccountView(
    Long id,
    String acct,
    String username,
    String domain,
    String displayName,
    String avatarUrl,
    String url,
    boolean following,
    boolean requested,
    boolean domainBlocked,
    boolean blocking,
    boolean muting) {

  static RemoteAccountView of(RemoteAccountMatch match) {
    return new RemoteAccountView(
        match.id(),
        match.username() + "@" + match.domain(),
        match.username(),
        match.domain(),
        match.displayName(),
        match.avatarUrl(),
        match.url(),
        match.following(),
        match.requested(),
        false,
        false,
        match.muting());
  }

  static RemoteAccountView of(
      RemoteActorEntity actor,
      Optional<FederationFollowingEntity> following,
      RemoteAccountStanding standing) {
    String username = actor.getUsername() == null ? "" : actor.getUsername();
    return new RemoteAccountView(
        actor.getId(),
        username + "@" + actor.getDomain(),
        username,
        actor.getDomain(),
        actor.getDisplayName(),
        actor.getAvatarUrl(),
        actor.getProfileUrl() == null ? actor.getActorUri() : actor.getProfileUrl(),
        following.map(FederationFollowingEntity::accepted).orElse(false),
        following.map(row -> !row.accepted()).orElse(false),
        standing.domainBlocked(),
        standing.blocking(),
        standing.muting());
  }
}
