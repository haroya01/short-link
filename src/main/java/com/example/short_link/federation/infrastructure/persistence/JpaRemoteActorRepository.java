package com.example.short_link.federation.infrastructure.persistence;

import com.example.short_link.federation.domain.RemoteActorEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaRemoteActorRepository extends JpaRepository<RemoteActorEntity, Long> {

  Optional<RemoteActorEntity> findByActorUri(String actorUri);

  List<RemoteActorEntity> findAllByActorUriIn(Collection<String> actorUris);

  Optional<RemoteActorEntity> findFirstByKeyId(String keyId);

  Optional<RemoteActorEntity> findFirstByUsernameAndDomainOrUsernameAndAccountDomain(
      String username, String domain, String sameUsername, String accountDomain);
}
