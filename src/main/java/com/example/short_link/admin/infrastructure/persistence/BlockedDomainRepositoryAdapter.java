package com.example.short_link.admin.infrastructure.persistence;

import com.example.short_link.admin.domain.BlockedDomainEntity;
import com.example.short_link.admin.domain.repository.BlockedDomainRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class BlockedDomainRepositoryAdapter implements BlockedDomainRepository {

  private final JpaBlockedDomainRepository jpa;

  @Override
  public BlockedDomainEntity save(BlockedDomainEntity domain) {
    return jpa.save(domain);
  }

  @Override
  public void delete(BlockedDomainEntity domain) {
    jpa.delete(domain);
  }

  @Override
  public Optional<BlockedDomainEntity> findByDomain(String domain) {
    return jpa.findByDomain(domain);
  }

  @Override
  public List<BlockedDomainEntity> findAllByOrderByBlockedAtDesc() {
    return jpa.findAllByOrderByBlockedAtDesc();
  }

  @Override
  public List<String> findAllDomains() {
    return jpa.findAllDomains();
  }
}
