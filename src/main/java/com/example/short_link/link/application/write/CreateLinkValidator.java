package com.example.short_link.link.application.write;

import com.example.short_link.common.security.BlockedDomainChecker;
import com.example.short_link.link.application.helper.LinkUrlHasher;
import com.example.short_link.link.application.helper.ReservedShortCodes;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
import com.example.short_link.link.safety.application.UrlSafetyChecker;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Pre-transaction validation for link creation — self-reference, blocked-domain lookup, Safe
 * Browsing, reserved short codes. Lives outside the @Transactional boundary so the outbound
 * Safe-Browsing HTTP call doesn't hold a JDBC connection for the round-trip (pool starvation under
 * load).
 */
@Component
class CreateLinkValidator {

  private final BlockedDomainChecker blockedDomainChecker;
  private final UrlSafetyChecker urlSafetyChecker;
  private final MeterRegistry meterRegistry;
  private final String shortLinkHost;

  CreateLinkValidator(
      BlockedDomainChecker blockedDomainChecker,
      UrlSafetyChecker urlSafetyChecker,
      MeterRegistry meterRegistry,
      @Value("${short-link.base-url}") String baseUrl) {
    this.blockedDomainChecker = blockedDomainChecker;
    this.urlSafetyChecker = urlSafetyChecker;
    this.meterRegistry = meterRegistry;
    this.shortLinkHost = canonicalHost(baseUrl);
  }

  void validateUrl(String url) {
    rejectSelfReference(url);
    if (blockedDomainChecker.isBlocked(url)) {
      meterRegistry.counter("short_link.created", "result", "blocked_domain").increment();
      throw new LinkException(LinkErrorCode.MALICIOUS_URL, LinkUrlHasher.sha256Prefix(url));
    }
    if (!urlSafetyChecker.isSafe(url)) {
      throw new LinkException(LinkErrorCode.MALICIOUS_URL, LinkUrlHasher.sha256Prefix(url));
    }
  }

  /**
   * Rejects URLs that point back at the short-link host itself (apex and its www variant) —
   * re-shortening our own short links invites redirect loops/chains. Subdomains (blog.kurl.me,
   * author blogs) are real content and stay allowed.
   */
  void rejectSelfReference(String url) {
    String host = canonicalHost(url);
    if (host != null && host.equals(shortLinkHost)) {
      meterRegistry.counter("short_link.created", "result", "self_reference").increment();
      throw new LinkException(LinkErrorCode.SELF_REFERENCING_URL, host);
    }
  }

  void rejectIfReserved(String code) {
    if (code != null && ReservedShortCodes.isReserved(code)) {
      throw new LinkException(LinkErrorCode.RESERVED_SHORT_CODE, code);
    }
  }

  /** Lower-cased host with any leading {@code www.} stripped, or {@code null} if unparseable. */
  private static String canonicalHost(String url) {
    if (url == null) {
      return null;
    }
    String host;
    try {
      host = new URI(url.trim()).getHost();
    } catch (URISyntaxException e) {
      return null;
    }
    if (host == null) {
      return null;
    }
    host = host.toLowerCase(Locale.ROOT);
    return host.startsWith("www.") ? host.substring("www.".length()) : host;
  }
}
