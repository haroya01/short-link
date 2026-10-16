package com.example.short_link.federation.infrastructure.persistence;

import com.example.short_link.federation.domain.FederationDirectorySourceEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaFederationDirectorySourceRepository
    extends JpaRepository<FederationDirectorySourceEntity, String> {

  List<FederationDirectorySourceEntity> findAllByOrderByHostAsc();
}
