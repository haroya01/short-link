package com.example.short_link.federation.infrastructure.persistence;

import com.example.short_link.federation.domain.FederationDirectorySourceEntity;
import com.example.short_link.federation.domain.repository.FederationDirectorySourceRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class FederationDirectorySourceRepositoryAdapter implements FederationDirectorySourceRepository {

  private final JpaFederationDirectorySourceRepository jpa;

  @Override
  public List<FederationDirectorySourceEntity> list() {
    return jpa.findAllByOrderByHostAsc();
  }

  @Override
  public Optional<FederationDirectorySourceEntity> find(String host) {
    return jpa.findById(host);
  }

  @Override
  public FederationDirectorySourceEntity save(FederationDirectorySourceEntity source) {
    return jpa.save(source);
  }

  @Override
  public void delete(FederationDirectorySourceEntity source) {
    jpa.delete(source);
  }
}
