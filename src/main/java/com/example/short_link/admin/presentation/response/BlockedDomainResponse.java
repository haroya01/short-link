package com.example.short_link.admin.presentation.response;

import com.example.short_link.admin.domain.BlockedDomainEntity;
import java.time.Instant;

public record BlockedDomainResponse(
    Long id, String domain, String reason, Long blockedByUserId, Instant blockedAt) {

  public static BlockedDomainResponse from(BlockedDomainEntity e) {
    return new BlockedDomainResponse(
        e.getId(), e.getDomain(), e.getReason(), e.getBlockedByUserId(), e.getBlockedAt());
  }
}
