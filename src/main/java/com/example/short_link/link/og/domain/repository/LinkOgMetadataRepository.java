package com.example.short_link.link.og.domain.repository;

import com.example.short_link.link.og.domain.LinkOgMetadataEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LinkOgMetadataRepository extends JpaRepository<LinkOgMetadataEntity, Long> {}
