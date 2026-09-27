package com.example.short_link.admin.presentation.response;

import com.example.short_link.admin.domain.BlockedDomainEntity;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BlockedDomainResponse(
    Long id,
    String domain,
    String reason,
    Long blockedByUserId,
    Instant blockedAt,
    Integer warnedOwners) {

  public static BlockedDomainResponse from(BlockedDomainEntity e) {
    return new BlockedDomainResponse(
        e.getId(), e.getDomain(), e.getReason(), e.getBlockedByUserId(), e.getBlockedAt(), null);
  }

  public static BlockedDomainResponse from(BlockedDomainEntity e, int warnedOwners) {
    return new BlockedDomainResponse(
        e.getId(),
        e.getDomain(),
        e.getReason(),
        e.getBlockedByUserId(),
        e.getBlockedAt(),
        warnedOwners);
  }
}
