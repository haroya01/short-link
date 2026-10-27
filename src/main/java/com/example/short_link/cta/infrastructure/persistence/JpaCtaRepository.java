package com.example.short_link.cta.infrastructure.persistence;

import com.example.short_link.cta.domain.CtaEntity;
import com.example.short_link.cta.domain.repository.CtaRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaCtaRepository extends JpaRepository<CtaEntity, Long>, CtaRepository {}
