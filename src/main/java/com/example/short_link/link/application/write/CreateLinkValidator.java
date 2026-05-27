package com.example.short_link.link.application.write;

import com.example.short_link.common.security.BlockedDomainChecker;
import com.example.short_link.link.application.helper.LinkUrlHasher;
import com.example.short_link.link.application.helper.ReservedShortCodes;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
import com.example.short_link.link.safety.application.UrlSafetyChecker;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Pre-transaction validation for link creation — blocked-domain lookup, Safe Browsing, reserved
 * short codes. Lives outside the @Transactional boundary so the outbound Safe-Browsing HTTP call
 * doesn't hold a JDBC connection for the round-trip (pool starvation under load).
 */
@Component
@RequiredArgsConstructor
class CreateLinkValidator {

  private final BlockedDomainChecker blockedDomainChecker;
  private final UrlSafetyChecker urlSafetyChecker;
  private final MeterRegistry meterRegistry;

  void validateUrl(String url) {
    if (blockedDomainChecker.isBlocked(url)) {
      meterRegistry.counter("short_link.created", "result", "blocked_domain").increment();
      throw new LinkException(LinkErrorCode.MALICIOUS_URL, LinkUrlHasher.sha256Prefix(url));
    }
    if (!urlSafetyChecker.isSafe(url)) {
      throw new LinkException(LinkErrorCode.MALICIOUS_URL, LinkUrlHasher.sha256Prefix(url));
    }
  }

  void rejectIfReserved(String code) {
    if (code != null && ReservedShortCodes.isReserved(code)) {
      throw new LinkException(LinkErrorCode.RESERVED_SHORT_CODE, code);
    }
  }
}
