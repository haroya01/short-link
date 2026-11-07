package com.example.short_link.link.safety.application;

import com.example.short_link.common.security.BlockedDomainChecker;
import com.example.short_link.common.security.OutboundUrlPolicy;
import com.example.short_link.link.domain.WebAddress;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class OutboundUrlSafety implements OutboundUrlPolicy {

  private final BlockedDomainChecker blockedDomains;
  private final UrlSafetyChecker safety;

  @Override
  public boolean allows(String url) {
    return WebAddress.is(url) && !blockedDomains.isBlocked(url) && safety.isSafe(url);
  }
}
