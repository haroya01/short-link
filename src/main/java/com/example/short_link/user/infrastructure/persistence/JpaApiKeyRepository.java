package com.example.short_link.user.infrastructure.persistence;

import com.example.short_link.user.domain.ApiKeyEntity;
import com.example.short_link.user.domain.repository.ApiKeyRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaApiKeyRepository extends JpaRepository<ApiKeyEntity, Long>, ApiKeyRepository {}
