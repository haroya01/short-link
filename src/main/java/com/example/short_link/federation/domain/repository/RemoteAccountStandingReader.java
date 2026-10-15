package com.example.short_link.federation.domain.repository;

import com.example.short_link.federation.domain.RemoteAccountStanding;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;

public interface RemoteAccountStandingReader {

  Map<Long, RemoteAccountStanding> of(Long userId, Collection<Long> remoteActorIds, Instant now);
}
