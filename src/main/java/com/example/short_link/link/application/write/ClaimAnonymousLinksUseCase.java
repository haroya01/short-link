package com.example.short_link.link.application.write;

import com.example.short_link.link.application.dto.ClaimResult;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Claims only still-anonymous links with matching unused tokens; replayed tokens are no-ops. */
@Service
@RequiredArgsConstructor
public class ClaimAnonymousLinksUseCase {

  public static final int MAX_TOKENS_PER_REQUEST = 50;

  private final LinkRepository repository;
  private final MeterRegistry meterRegistry;

  @Transactional
  public ClaimResult execute(ClaimAnonymousLinksCommand command) {
    if (command.claimTokens().isEmpty()) {
      return new ClaimResult(0, 0);
    }
    List<String> bounded = command.claimTokens().stream().limit(MAX_TOKENS_PER_REQUEST).toList();
    List<LinkEntity> matches = repository.findAllByClaimTokenInAndUserIdIsNull(bounded);
    int claimed = 0;
    for (LinkEntity link : matches) {
      link.claim(command.userId());
      claimed++;
    }
    int skipped = bounded.size() - claimed;
    meterRegistry.counter("link.claim", "result", "ok").increment(claimed);
    meterRegistry.counter("link.claim", "result", "skipped").increment(skipped);
    return new ClaimResult(claimed, skipped);
  }
}
