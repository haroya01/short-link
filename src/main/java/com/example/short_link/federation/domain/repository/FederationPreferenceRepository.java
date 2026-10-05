package com.example.short_link.federation.domain.repository;

import com.example.short_link.federation.domain.FederationPreferenceEntity;
import java.util.Optional;

public interface FederationPreferenceRepository {

  Optional<FederationPreferenceEntity> find(Long userId);

  void insert(FederationPreferenceEntity preference);
}
