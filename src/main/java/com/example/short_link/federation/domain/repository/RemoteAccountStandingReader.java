package com.example.short_link.federation.domain.repository;

import com.example.short_link.federation.domain.RemoteAccountStanding;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

public interface RemoteAccountStandingReader {

  Map<Long, RemoteAccountStanding> of(Long userId, Collection<Long> remoteActorIds, Instant now);

  // Of these accounts (by ActivityPub id) and servers, the ones the member blocked or muted.
  Hidden hidden(Long userId, Collection<String> actorUris, Collection<String> domains, Instant now);

  record Hidden(Set<String> actorUris, Set<String> domains) {

    public static final Hidden NONE = new Hidden(Set.of(), Set.of());
  }
}
