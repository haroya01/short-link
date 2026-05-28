package com.example.short_link.link.og.infrastructure.persistence;

import com.example.short_link.link.og.domain.LinkOgMetadataEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaLinkOgMetadataRepository extends JpaRepository<LinkOgMetadataEntity, Long> {}
