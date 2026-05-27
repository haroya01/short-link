package com.example.short_link.admin.application.write;

import com.example.short_link.admin.application.helper.BlockedDomainNormalizer;
import com.example.short_link.admin.application.read.BlockedDomainCache;
import com.example.short_link.admin.domain.repository.BlockedDomainRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UnblockDomainUseCase {

  private final BlockedDomainRepository repository;
  private final BlockedDomainCache blockedDomainCache;

  @Transactional
  public boolean execute(String rawDomain) {
    String normalized = BlockedDomainNormalizer.normalize(rawDomain);
    if (normalized == null) return false;
    boolean removed =
        repository
            .findByDomain(normalized)
            .map(
                entity -> {
                  repository.delete(entity);
                  return true;
                })
            .orElse(false);
    blockedDomainCache.evictAfterCommit();
    return removed;
  }
}
