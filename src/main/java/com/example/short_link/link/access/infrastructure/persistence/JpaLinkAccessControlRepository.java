package com.example.short_link.link.access.infrastructure.persistence;

import com.example.short_link.link.access.domain.LinkAccessControlEntity;
import com.example.short_link.link.access.domain.repository.LinkAccessControlRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaLinkAccessControlRepository
    extends JpaRepository<LinkAccessControlEntity, Long>, LinkAccessControlRepository {}
