package com.example.short_link.federation.domain.repository;

import com.example.short_link.federation.domain.FederationRelayEntity;
import com.example.short_link.federation.domain.FederationRelaySettingEntity;
import java.util.List;
import java.util.Optional;

public interface FederationRelayRepository {

  List<FederationRelayEntity> list();

  Optional<FederationRelayEntity> findById(Long id);

  Optional<FederationRelayEntity> findByActorUri(String actorUri);

  Optional<FederationRelayEntity> findByFollowActivityId(String followActivityId);

  FederationRelayEntity save(FederationRelayEntity relay);

  void delete(FederationRelayEntity relay);

  FederationRelaySettingEntity setting();

  FederationRelaySettingEntity save(FederationRelaySettingEntity setting);
}
