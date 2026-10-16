package com.example.short_link.federation.domain.repository;

import com.example.short_link.federation.domain.RemoteAccountMatch;
import com.example.short_link.federation.domain.RemoteActorEntity;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RemoteActorRepository {

  Optional<RemoteActorEntity> findByActorUri(String actorUri);

  List<RemoteActorEntity> findAllByActorUri(Collection<String> actorUris);

  Optional<RemoteActorEntity> findByKeyId(String keyId);

  Optional<RemoteActorEntity> findById(Long id);

  Optional<RemoteActorEntity> findByAcct(String username, String domain);

  List<RemoteActorEntity> findAllById(Collection<Long> ids);

  List<RemoteAccountMatch> search(Long viewerId, String term, int limit, Instant now);

  List<RemoteAccountMatch> suggestions(Long viewerId, int limit, Instant now);

  RemoteActorEntity saveAndFlush(RemoteActorEntity actor);

  // True for the one caller that marks the outbox looked at; false when it was looked at since.
  boolean claimOutbox(Long id, Instant at, Instant lookedAtBefore);

  // The accounts a directory listed: seen at the given time and, as the directory says, findable.
  int markListed(Collection<Long> ids, Instant at);

  // Accounts once listed with no word of consent since the cutoff, neither from a directory nor
  // from
  // their own server, stop being findable by name.
  int forgetUnlisted(Instant cutoff);

  void delete(RemoteActorEntity actor);
}
