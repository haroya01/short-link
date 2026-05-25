package com.example.short_link.link.domain.repository;

import com.example.short_link.link.domain.LinkExpirationPolicyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LinkExpirationPolicyRepository
    extends JpaRepository<LinkExpirationPolicyEntity, Long> {}
