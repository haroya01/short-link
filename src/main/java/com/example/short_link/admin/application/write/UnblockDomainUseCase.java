package com.example.short_link.admin.application.write;

import com.example.short_link.admin.application.helper.BlockedDomainNormalizer;
import com.example.short_link.admin.domain.repository.BlockedDomainRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UnblockDomainUseCase {

  private final BlockedDomainRepository repository;

  @Transactional
  @CacheEvict(value = "blocked-domains", allEntries = true)
  public boolean execute(String rawDomain) {
    String normalized = BlockedDomainNormalizer.normalize(rawDomain);
    if (normalized == null) return false;
    return repository
        .findByDomain(normalized)
        .map(
            entity -> {
              repository.delete(entity);
              return true;
            })
        .orElse(false);
  }
}
