package com.example.short_link.federation.infrastructure.persistence;

import com.example.short_link.federation.domain.FederationActorEntity;
import com.example.short_link.federation.domain.repository.FederationActorRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class FederationActorRepositoryAdapter implements FederationActorRepository {

  private final JpaFederationActorRepository jpa;

  @Override
  public Optional<FederationActorEntity> findByUserId(Long userId) {
    return jpa.findById(userId);
  }

  @Override
  public Optional<FederationActorEntity> findByPublicId(String publicId) {
    return jpa.findByPublicId(publicId);
  }

  @Override
  public FederationActorEntity saveAndFlush(FederationActorEntity actor) {
    return jpa.saveAndFlush(actor);
  }
}
