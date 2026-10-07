package com.example.short_link.federation.infrastructure.persistence;

import com.example.short_link.federation.domain.FederationActorEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaFederationActorRepository extends JpaRepository<FederationActorEntity, Long> {

  Optional<FederationActorEntity> findByPublicId(String publicId);

  List<FederationActorEntity> findByPublicIdIn(Collection<String> publicIds);
}
