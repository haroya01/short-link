package com.example.short_link.federation.domain.repository;

import com.example.short_link.federation.domain.FederationActorEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FederationActorRepository {

  Optional<FederationActorEntity> findByUserId(Long userId);

  Optional<FederationActorEntity> findByPublicId(String publicId);

  List<FederationActorEntity> findByPublicIds(Collection<String> publicIds);

  FederationActorEntity saveAndFlush(FederationActorEntity actor);
}
