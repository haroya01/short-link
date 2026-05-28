package com.example.short_link.link.profilebinding.infrastructure.persistence;

import com.example.short_link.link.profilebinding.domain.LinkProfileBindingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaLinkProfileBindingRepository
    extends JpaRepository<LinkProfileBindingEntity, Long> {}
