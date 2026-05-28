package com.example.short_link.link.expiration.domain.repository;

import com.example.short_link.link.expiration.domain.LinkExpirationPolicyEntity;
import java.util.Optional;

public interface LinkExpirationPolicyRepository {

  Optional<LinkExpirationPolicyEntity> findById(Long id);

  LinkExpirationPolicyEntity save(LinkExpirationPolicyEntity policy);
}
