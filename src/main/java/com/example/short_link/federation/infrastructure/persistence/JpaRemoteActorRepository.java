package com.example.short_link.federation.infrastructure.persistence;

import com.example.short_link.federation.domain.RemoteActorEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaRemoteActorRepository extends JpaRepository<RemoteActorEntity, Long> {

  Optional<RemoteActorEntity> findByActorUri(String actorUri);

  Optional<RemoteActorEntity> findFirstByKeyId(String keyId);

  Optional<RemoteActorEntity> findFirstByDomainAndUsername(String domain, String username);
}
