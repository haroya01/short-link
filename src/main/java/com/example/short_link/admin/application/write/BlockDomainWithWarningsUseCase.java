package com.example.short_link.admin.application.write;

import com.example.short_link.admin.domain.BlockedDomainEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BlockDomainWithWarningsUseCase {

  private final BlockDomainUseCase blockDomain;
  private final BlockedDomainWarningFanout warnings;

  // Do not add a transaction here: the existing block operation commits before fan-out.
  public Result execute(String domain, String reason, Long actorUserId) {
    BlockedDomainEntity blocked = blockDomain.execute(domain, reason, actorUserId);
    return new Result(blocked, warnings.execute(blocked.getDomain()));
  }

  public record Result(BlockedDomainEntity domain, int warnedOwners) {}
}
