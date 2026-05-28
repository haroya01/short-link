package com.example.short_link.admin.application.write;

import com.example.short_link.admin.application.helper.BlockedDomainNormalizer;
import com.example.short_link.admin.application.read.BlockedDomainCache;
import com.example.short_link.admin.domain.BlockedDomainEntity;
import com.example.short_link.admin.domain.repository.BlockedDomainRepository;
import com.example.short_link.admin.exception.AdminErrorCode;
import com.example.short_link.admin.exception.AdminException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BlockDomainUseCase {

  private final BlockedDomainRepository repository;
  private final BlockedDomainCache blockedDomainCache;

  @Transactional
  public BlockedDomainEntity execute(String rawDomain, String reason, Long actorUserId) {
    String normalized = BlockedDomainNormalizer.normalize(rawDomain);
    if (normalized == null) {
      throw new AdminException(AdminErrorCode.INVALID_DOMAIN, rawDomain);
    }
    BlockedDomainEntity blockedDomain =
        repository
            .findByDomain(normalized)
            .orElseGet(
                () -> repository.save(new BlockedDomainEntity(normalized, reason, actorUserId)));
    blockedDomainCache.evictAfterCommit();
    return blockedDomain;
  }
}
