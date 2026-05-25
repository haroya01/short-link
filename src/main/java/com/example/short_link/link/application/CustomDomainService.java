package com.example.short_link.link.application;

import com.example.short_link.link.application.helper.TxtResolver;
import com.example.short_link.link.domain.CustomDomainEntity;
import com.example.short_link.link.domain.repository.CustomDomainRepository;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
import io.micrometer.core.instrument.MeterRegistry;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages user-owned custom domains for short-link routing.
 *
 * <p>The verification flow: register a domain, receive a token, place a TXT record at {@code
 * _kurl-verify.<domain>} with that exact value, then call verify. TXT lookups go through {@link
 * TxtResolver} so verification doesn't depend on any third-party API and is injectable for tests.
 *
 * <p>TLS handling is the user's responsibility — they typically front their domain with Cloudflare
 * (free) and CNAME to {@code kurl.me}, which terminates TLS at the CDN edge before we see the
 * request.
 */
@Service
@RequiredArgsConstructor
public class CustomDomainService {

  public static final int MAX_PER_USER = 5;

  /**
   * After registration, the auto-verify job polls DNS for this long. Beyond it, the user has to hit
   * the manual /verify endpoint themselves — covers cases where the DNS provider takes longer than
   * a usual TTL window to propagate.
   */
  public static final Duration AUTO_VERIFY_WINDOW = Duration.ofMinutes(10);

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final HexFormat HEX = HexFormat.of();
  private static final String TXT_PREFIX = "_kurl-verify.";

  private final CustomDomainRepository repository;
  private final MeterRegistry meterRegistry;
  private final TxtResolver txtResolver;

  @Transactional(readOnly = true)
  public List<DomainSummary> list(Long userId) {
    return repository.findAllByUserIdOrderByIdAsc(userId).stream().map(this::toSummary).toList();
  }

  @Transactional
  public DomainSummary register(Long userId, String rawDomain) {
    String domain = normalize(rawDomain);
    validate(domain);
    if (repository.existsByDomain(domain)) {
      throw new IllegalArgumentException("domain already registered");
    }
    if (repository.findAllByUserIdOrderByIdAsc(userId).size() >= MAX_PER_USER) {
      throw new IllegalArgumentException("max " + MAX_PER_USER + " custom domains per user");
    }
    String token = "kurl-verify=" + HEX.formatHex(randomBytes(16));
    CustomDomainEntity saved = repository.save(new CustomDomainEntity(userId, domain, token));
    meterRegistry.counter("custom_domain.registered").increment();
    return toSummary(saved);
  }

  @Transactional
  public DomainSummary verify(Long userId, Long domainId) {
    CustomDomainEntity entity = ownedDomain(userId, domainId);
    boolean ok = checkTxtRecord(entity.getDomain(), entity.getVerificationToken());
    if (!ok) {
      entity.markCheckFailed();
      meterRegistry.counter("custom_domain.verify", "result", "failed").increment();
      throw new LinkException(LinkErrorCode.CUSTOM_DOMAIN_NOT_VERIFIED, entity.getDomain());
    }
    entity.markVerified();
    meterRegistry.counter("custom_domain.verify", "result", "ok").increment();
    return toSummary(entity);
  }

  /**
   * Probe one pending domain on behalf of the auto-verify job. Returns true when DNS now resolves
   * the expected TXT — caller persists the state change. Never throws on DNS misses; that's the
   * normal "still propagating" path and the next tick will retry.
   */
  @Transactional
  public boolean autoVerifyOne(CustomDomainEntity entity) {
    boolean ok = checkTxtRecord(entity.getDomain(), entity.getVerificationToken());
    CustomDomainEntity reloaded = repository.findById(entity.getId()).orElse(null);
    if (reloaded == null) return false;
    if (ok) {
      reloaded.markVerified();
      meterRegistry.counter("custom_domain.verify", "result", "auto_ok").increment();
      return true;
    }
    reloaded.markCheckFailed();
    return false;
  }

  @Transactional(readOnly = true)
  public List<CustomDomainEntity> findPendingWithinWindow() {
    Instant cutoff = Instant.now().minus(AUTO_VERIFY_WINDOW);
    return repository.findAllByVerifiedFalseAndCreatedAtAfter(cutoff);
  }

  @Transactional
  public void delete(Long userId, Long domainId) {
    CustomDomainEntity entity = ownedDomain(userId, domainId);
    repository.delete(entity);
    meterRegistry.counter("custom_domain.deleted").increment();
  }

  @Transactional(readOnly = true)
  public Long resolveOwner(String hostHeader) {
    if (hostHeader == null || hostHeader.isBlank()) return null;
    String domain = normalize(hostHeader);
    return repository
        .findByDomain(domain)
        .filter(CustomDomainEntity::isVerified)
        .map(CustomDomainEntity::getUserId)
        .orElse(null);
  }

  private boolean checkTxtRecord(String domain, String expectedToken) {
    for (String value : txtResolver.lookup(TXT_PREFIX + domain)) {
      if (value.equals(expectedToken)) return true;
    }
    return false;
  }

  private CustomDomainEntity ownedDomain(Long userId, Long id) {
    CustomDomainEntity entity =
        repository
            .findById(id)
            .orElseThrow(() -> new LinkException(LinkErrorCode.CUSTOM_DOMAIN_NOT_FOUND));
    if (!entity.getUserId().equals(userId)) {
      throw new LinkException(LinkErrorCode.CUSTOM_DOMAIN_NOT_FOUND);
    }
    return entity;
  }

  private DomainSummary toSummary(CustomDomainEntity e) {
    Instant autoUntil = e.isVerified() ? null : e.getCreatedAt().plus(AUTO_VERIFY_WINDOW);
    return new DomainSummary(
        e.getId(),
        e.getDomain(),
        e.getVerificationToken(),
        TXT_PREFIX + e.getDomain(),
        e.isVerified(),
        e.getVerifiedAt(),
        e.getLastCheckedAt(),
        e.getCreatedAt(),
        autoUntil);
  }

  private static String normalize(String input) {
    return input.trim().toLowerCase().replaceAll("^https?://", "").replaceAll("/.*$", "");
  }

  private static void validate(String domain) {
    if (domain.isBlank() || domain.length() > 253) {
      throw new IllegalArgumentException("invalid domain");
    }
    if (!domain.matches("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$")) {
      throw new IllegalArgumentException("invalid domain format");
    }
    // Block our own domains so users can't claim them.
    if (domain.equals("kurl.me") || domain.endsWith(".kurl.me")) {
      throw new IllegalArgumentException("cannot register kurl.me itself");
    }
  }

  private static byte[] randomBytes(int n) {
    byte[] buf = new byte[n];
    RANDOM.nextBytes(buf);
    return buf;
  }

  public record DomainSummary(
      Long id,
      String domain,
      String verificationToken,
      String verificationHost,
      boolean verified,
      Instant verifiedAt,
      Instant lastCheckedAt,
      Instant createdAt,
      Instant autoVerifyUntil) {}
}
