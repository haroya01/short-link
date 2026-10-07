package com.example.short_link.federation.infrastructure.persistence;

import com.example.short_link.federation.domain.RemoteActorEntity;
import com.example.short_link.federation.domain.repository.RemoteActorRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class RemoteActorRepositoryAdapter implements RemoteActorRepository {

  private final JpaRemoteActorRepository jpa;

  @Override
  public Optional<RemoteActorEntity> findByActorUri(String actorUri) {
    return jpa.findByActorUri(actorUri);
  }

  @Override
  public Optional<RemoteActorEntity> findByKeyId(String keyId) {
    return jpa.findFirstByKeyId(keyId);
  }

  @Override
  public Optional<RemoteActorEntity> findById(Long id) {
    return jpa.findById(id);
  }

  @Override
  public Optional<RemoteActorEntity> findByAcct(String username, String domain) {
    return jpa.findFirstByDomainAndUsername(domain, username);
  }

  @Override
  public List<RemoteActorEntity> findAllById(Collection<Long> ids) {
    return ids.isEmpty() ? List.of() : jpa.findAllById(ids);
  }

  @Override
  public RemoteActorEntity saveAndFlush(RemoteActorEntity actor) {
    return jpa.saveAndFlush(actor);
  }

  @Override
  public void delete(RemoteActorEntity actor) {
    jpa.delete(actor);
  }
}
