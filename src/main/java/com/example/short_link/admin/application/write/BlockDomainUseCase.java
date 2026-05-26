package com.example.short_link.admin.application.write;

import com.example.short_link.admin.application.helper.BlockedDomainNormalizer;
import com.example.short_link.admin.domain.BlockedDomainEntity;
import com.example.short_link.admin.domain.repository.BlockedDomainRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BlockDomainUseCase {

  private final BlockedDomainRepository repository;

  @Transactional
  @CacheEvict(value = "blocked-domains", allEntries = true)
  public BlockedDomainEntity execute(String rawDomain, String reason, Long actorUserId) {
    String normalized = BlockedDomainNormalizer.normalize(rawDomain);
    if (normalized == null) {
      throw new IllegalArgumentException("invalid domain");
    }
    return repository
        .findByDomain(normalized)
        .orElseGet(() -> repository.save(new BlockedDomainEntity(normalized, reason, actorUserId)));
  }
}
