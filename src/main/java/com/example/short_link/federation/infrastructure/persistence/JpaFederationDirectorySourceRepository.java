package com.example.short_link.federation.infrastructure.persistence;

import com.example.short_link.federation.domain.FederationDirectorySourceEntity;
import com.example.short_link.federation.domain.repository.FederationDirectorySourceRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaFederationDirectorySourceRepository
    extends JpaRepository<FederationDirectorySourceEntity, String>,
        FederationDirectorySourceRepository {}
