package com.example.short_link.federation.infrastructure.persistence;

import com.example.short_link.federation.domain.FederationRelaySettingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaFederationRelaySettingRepository
    extends JpaRepository<FederationRelaySettingEntity, Byte> {}
