package com.example.short_link.admin.application.write;

import com.example.short_link.admin.application.helper.BlockedDomainNormalizer;
import com.example.short_link.admin.application.read.BlockedDomainCache;
import com.example.short_link.admin.domain.BlockedDomainEntity;
import com.example.short_link.admin.domain.repository.BlockedDomainRepository;
import com.example.short_link.admin.exception.AdminErrorCode;
import com.example.short_link.admin.exception.AdminException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BlockDomainUseCase {

  private final BlockedDomainRepository repository;
  private final BlockedDomainCache blockedDomainCache;

  /** 자기 서비스 호스트 — 이 도메인들의 차단은 자기 참조 링크 전체를 죽이므로 거부한다. */
  private final List<String> selfHosts;

  public BlockDomainUseCase(
      BlockedDomainRepository repository,
      BlockedDomainCache blockedDomainCache,
      @Value("${short-link.base-url}") String baseUrl,
      @Value("${short-link.frontend-base-url}") String frontendBaseUrl) {
    this.repository = repository;
    this.blockedDomainCache = blockedDomainCache;
    this.selfHosts =
        Stream.of(baseUrl, frontendBaseUrl)
            .map(BlockedDomainNormalizer::hostOf)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
  }

  @Transactional
  public BlockedDomainEntity execute(String rawDomain, String reason, Long actorUserId) {
    String normalized = BlockedDomainNormalizer.normalize(rawDomain);
    if (normalized == null) {
      throw new AdminException(AdminErrorCode.INVALID_DOMAIN, rawDomain);
    }
    if (isSelfDomain(normalized)) {
      throw new AdminException(AdminErrorCode.SELF_DOMAIN_BLOCK, normalized);
    }
    BlockedDomainEntity blockedDomain =
        repository
            .findByDomain(normalized)
            .orElseGet(
                () -> repository.save(new BlockedDomainEntity(normalized, reason, actorUserId)));
    blockedDomainCache.evictAfterCommit();
    return blockedDomain;
  }

  /* apex 를 막으면 서브도메인 호스트도, 서브도메인을 막으면 그 자신도 걸리게 양방향 접미사 비교.
  (08-19: 신고 처리 중 kurl.me 차단 → 자기 참조 링크 전부 403, prod-smoke 경보) */
  private boolean isSelfDomain(String normalized) {
    for (String host : selfHosts) {
      if (normalized.equals(host)
          || host.endsWith("." + normalized)
          || normalized.endsWith("." + host)) {
        return true;
      }
    }
    return false;
  }
}
