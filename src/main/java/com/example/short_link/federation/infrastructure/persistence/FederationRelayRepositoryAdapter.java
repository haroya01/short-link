package com.example.short_link.federation.infrastructure.persistence;

import com.example.short_link.federation.domain.FederationRelayEntity;
import com.example.short_link.federation.domain.FederationRelaySettingEntity;
import com.example.short_link.federation.domain.repository.FederationRelayRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class FederationRelayRepositoryAdapter implements FederationRelayRepository {

  private final JpaFederationRelayRepository relays;
  private final JpaFederationRelaySettingRepository settings;

  @Override
  public List<FederationRelayEntity> list() {
    return relays.findAllByOrderByIdAsc();
  }

  @Override
  public Optional<FederationRelayEntity> findById(Long id) {
    return relays.findById(id);
  }

  @Override
  public Optional<FederationRelayEntity> findByActorUri(String actorUri) {
    return relays.findByActorUri(actorUri);
  }

  @Override
  public Optional<FederationRelayEntity> findByFollowActivityId(String followActivityId) {
    return relays.findByFollowActivityId(followActivityId);
  }

  @Override
  public FederationRelayEntity save(FederationRelayEntity relay) {
    return relays.save(relay);
  }

  @Override
  public void delete(FederationRelayEntity relay) {
    relays.delete(relay);
  }

  @Override
  public FederationRelaySettingEntity setting() {
    return settings.findById(FederationRelaySettingEntity.SINGLETON_ID).orElseThrow();
  }

  @Override
  public FederationRelaySettingEntity save(FederationRelaySettingEntity setting) {
    return settings.save(setting);
  }
}
