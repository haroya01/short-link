package com.example.short_link.federation.infrastructure.persistence;

import com.example.short_link.federation.domain.FederationRelayEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaFederationRelayRepository extends JpaRepository<FederationRelayEntity, Long> {

  List<FederationRelayEntity> findAllByOrderByIdAsc();

  Optional<FederationRelayEntity> findByActorUri(String actorUri);

  Optional<FederationRelayEntity> findByFollowActivityId(String followActivityId);
}
