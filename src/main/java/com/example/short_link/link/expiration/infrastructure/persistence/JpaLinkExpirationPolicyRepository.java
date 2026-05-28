package com.example.short_link.link.expiration.infrastructure.persistence;

import com.example.short_link.link.expiration.domain.LinkExpirationPolicyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaLinkExpirationPolicyRepository
    extends JpaRepository<LinkExpirationPolicyEntity, Long> {}
