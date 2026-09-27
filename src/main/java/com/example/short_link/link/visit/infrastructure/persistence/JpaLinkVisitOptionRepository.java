package com.example.short_link.link.visit.infrastructure.persistence;

import com.example.short_link.link.visit.domain.LinkVisitOptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaLinkVisitOptionRepository extends JpaRepository<LinkVisitOptionEntity, Long> {}
