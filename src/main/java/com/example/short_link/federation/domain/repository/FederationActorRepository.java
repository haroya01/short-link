package com.example.short_link.federation.domain.repository;

import com.example.short_link.federation.domain.FederationActorEntity;
import java.util.Optional;

public interface FederationActorRepository {

  Optional<FederationActorEntity> findByUserId(Long userId);

  Optional<FederationActorEntity> findByPublicId(String publicId);

  FederationActorEntity saveAndFlush(FederationActorEntity actor);
}
