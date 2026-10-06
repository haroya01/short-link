package com.example.short_link.federation.domain.repository;

import com.example.short_link.federation.domain.RemoteActorEntity;
import java.util.Optional;

public interface RemoteActorRepository {

  Optional<RemoteActorEntity> findByActorUri(String actorUri);

  Optional<RemoteActorEntity> findByKeyId(String keyId);

  RemoteActorEntity saveAndFlush(RemoteActorEntity actor);
}
