package com.example.short_link.link.application.write;

import com.example.short_link.link.application.LinkCacheEviction;
import com.example.short_link.link.application.dto.ClaimResult;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClaimAnonymousLinksUseCase {

  public static final int MAX_TOKENS_PER_REQUEST = 50;

  private final LinkRepository repository;
  private final MeterRegistry meterRegistry;
  private final LinkCacheEviction linkCacheEviction;
  private final long quotaPerUser;

  public ClaimAnonymousLinksUseCase(
      LinkRepository repository,
      MeterRegistry meterRegistry,
      LinkCacheEviction linkCacheEviction,
      @Value("${short-link.link-quota.authenticated:200}") long quotaPerUser) {
    this.repository = repository;
    this.meterRegistry = meterRegistry;
    this.linkCacheEviction = linkCacheEviction;
    this.quotaPerUser = quotaPerUser;
  }

  @Transactional
  public ClaimResult execute(ClaimAnonymousLinksCommand command) {
    if (command.claimTokens().isEmpty()) {
      return new ClaimResult(0, 0);
    }
    List<String> bounded = command.claimTokens().stream().limit(MAX_TOKENS_PER_REQUEST).toList();
    List<LinkEntity> matches =
        withinQuota(command.userId(), repository.findAllByClaimTokenInAndUserIdIsNull(bounded));
    int claimed = 0;
    for (LinkEntity link : matches) {
      link.claim(command.userId());
      claimed++;
    }
    linkCacheEviction.evictAllAfterCommit(matches.stream().map(LinkEntity::getShortCode).toList());
    int skipped = bounded.size() - claimed;
    meterRegistry.counter("link.claim", "result", "ok").increment(claimed);
    meterRegistry.counter("link.claim", "result", "skipped").increment(skipped);
    return new ClaimResult(claimed, skipped);
  }

  private List<LinkEntity> withinQuota(Long userId, List<LinkEntity> found) {
    if (found.isEmpty()) return found;
    long room = Math.max(0, quotaPerUser - repository.countByUserId(userId));
    return found.stream().limit(room).toList();
  }
}
