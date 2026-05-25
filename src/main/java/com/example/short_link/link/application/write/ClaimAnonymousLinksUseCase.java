package com.example.short_link.link.application.write;

import com.example.short_link.link.application.dto.ClaimResult;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Claims anonymously-created links into a freshly authenticated account. Tokens were issued at
 * shorten time and stored in the client's localStorage; once the user signs up/logs in the client
 * sends them here. Only links that still have the matching unused token AND are still anonymous
 * (user_id IS NULL) are claimed — replays are no-ops.
 */
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
