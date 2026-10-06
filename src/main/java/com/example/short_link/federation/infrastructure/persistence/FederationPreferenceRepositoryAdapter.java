package com.example.short_link.federation.infrastructure.persistence;

import com.example.short_link.federation.domain.FederationPreferenceEntity;
import com.example.short_link.federation.domain.repository.FederationPreferenceRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class FederationPreferenceRepositoryAdapter implements FederationPreferenceRepository {

  private final JpaFederationPreferenceRepository jpa;

  @PersistenceContext private EntityManager em;

  @Override
  public Optional<FederationPreferenceEntity> find(Long userId) {
    return jpa.findById(userId);
  }

  // The id is the user's, so Spring Data's save() would merge (select first); a new row is
  // persisted.
  @Override
  public void insert(FederationPreferenceEntity preference) {
    em.persist(preference);
  }
}
