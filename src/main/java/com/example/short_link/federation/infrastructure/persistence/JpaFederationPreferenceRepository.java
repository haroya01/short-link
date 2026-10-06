package com.example.short_link.federation.infrastructure.persistence;

import com.example.short_link.federation.domain.FederationPreferenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaFederationPreferenceRepository
    extends JpaRepository<FederationPreferenceEntity, Long> {}
