package com.example.short_link.federation.infrastructure.persistence;

import com.example.short_link.federation.domain.FederationInstanceActorEntity;
import com.example.short_link.federation.domain.repository.FederationInstanceActorRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class FederationInstanceActorRepositoryAdapter implements FederationInstanceActorRepository {

  private final JpaFederationInstanceActorRepository jpa;

  @Override
  public Optional<FederationInstanceActorEntity> find() {
    return jpa.findById(FederationInstanceActorEntity.SINGLETON_ID);
  }

  @Override
  public FederationInstanceActorEntity saveAndFlush(FederationInstanceActorEntity actor) {
    return jpa.saveAndFlush(actor);
  }
}
