package com.example.short_link.link.expiration.infrastructure.persistence;

import com.example.short_link.link.expiration.domain.LinkExpirationPolicyEntity;
import com.example.short_link.link.expiration.domain.repository.LinkExpirationPolicyRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class LinkExpirationPolicyRepositoryAdapter implements LinkExpirationPolicyRepository {

  private final JpaLinkExpirationPolicyRepository jpa;

  @Override
  public Optional<LinkExpirationPolicyEntity> findById(Long id) {
    return jpa.findById(id);
  }

  @Override
  public LinkExpirationPolicyEntity save(LinkExpirationPolicyEntity policy) {
    return jpa.save(policy);
  }
}
