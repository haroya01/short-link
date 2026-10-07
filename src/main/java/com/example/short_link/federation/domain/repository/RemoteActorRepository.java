package com.example.short_link.federation.domain.repository;

import com.example.short_link.federation.domain.RemoteActorEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RemoteActorRepository {

  Optional<RemoteActorEntity> findByActorUri(String actorUri);

  Optional<RemoteActorEntity> findByKeyId(String keyId);

  Optional<RemoteActorEntity> findById(Long id);

  Optional<RemoteActorEntity> findByAcct(String username, String domain);

  List<RemoteActorEntity> findAllById(Collection<Long> ids);

  RemoteActorEntity saveAndFlush(RemoteActorEntity actor);

  void delete(RemoteActorEntity actor);
}
