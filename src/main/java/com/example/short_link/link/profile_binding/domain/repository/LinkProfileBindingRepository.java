package com.example.short_link.link.profile_binding.domain.repository;

import com.example.short_link.link.profile_binding.domain.LinkProfileBindingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LinkProfileBindingRepository
    extends JpaRepository<LinkProfileBindingEntity, Long> {}
