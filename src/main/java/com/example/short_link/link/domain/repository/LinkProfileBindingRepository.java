package com.example.short_link.link.domain.repository;

import com.example.short_link.link.domain.LinkProfileBindingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LinkProfileBindingRepository
    extends JpaRepository<LinkProfileBindingEntity, Long> {}
