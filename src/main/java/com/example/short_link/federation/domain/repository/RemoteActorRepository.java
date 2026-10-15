package com.example.short_link.federation.domain.repository;

import com.example.short_link.federation.domain.RemoteAccountMatch;
import com.example.short_link.federation.domain.RemoteActorEntity;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RemoteActorRepository {

  Optional<RemoteActorEntity> findByActorUri(String actorUri);

  Optional<RemoteActorEntity> findByKeyId(String keyId);

  Optional<RemoteActorEntity> findById(Long id);

  Optional<RemoteActorEntity> findByAcct(String username, String domain);

  List<RemoteActorEntity> findAllById(Collection<Long> ids);

  List<RemoteAccountMatch> search(Long viewerId, String term, int limit, Instant now);

  List<RemoteAccountMatch> suggestions(Long viewerId, int limit, Instant now);

  RemoteActorEntity saveAndFlush(RemoteActorEntity actor);

  // True for the one caller that marks the outbox looked at; false when it was looked at since.
  boolean claimOutbox(Long id, Instant at, Instant lookedAtBefore);

  void delete(RemoteActorEntity actor);
}
