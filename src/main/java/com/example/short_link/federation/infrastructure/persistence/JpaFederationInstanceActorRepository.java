package com.example.short_link.federation.infrastructure.persistence;

import com.example.short_link.federation.domain.FederationInstanceActorEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaFederationInstanceActorRepository
    extends JpaRepository<FederationInstanceActorEntity, Byte> {}
