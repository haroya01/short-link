package com.example.short_link.federation.domain.repository;

import com.example.short_link.federation.domain.FederationInstanceActorEntity;
import java.util.Optional;

public interface FederationInstanceActorRepository {

  Optional<FederationInstanceActorEntity> find();

  FederationInstanceActorEntity saveAndFlush(FederationInstanceActorEntity actor);
}
