package com.example.short_link.link.application;

import com.example.short_link.admin.application.BlockedDomainService;
import com.example.short_link.common.audit.AuditAction;
import com.example.short_link.common.audit.AuditLogService;
import com.example.short_link.link.application.dto.LinkCreated;
import com.example.short_link.link.application.dto.LinkOgFetchRequested;
import com.example.short_link.link.application.helper.LinkUrlHasher;
import com.example.short_link.link.application.helper.ReservedShortCodes;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkOgMetadataEntity;
import com.example.short_link.link.domain.repository.LinkOgMetadataRepository;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
import io.micrometer.core.instrument.MeterRegistry;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class LinkCreationService {

  private static final int MAX_ATTEMPTS = 5;
  private static final Duration ANONYMOUS_TTL = Duration.ofDays(1);
  private static final SecureRandom CLAIM_RANDOM = new SecureRandom();

  private final LinkRepository repository;
  private final LinkOgMetadataRepository ogMetadataRepository;
  private final ShortCodeGenerator generator;
  private final MeterRegistry meterRegistry;
  private final UrlSafetyChecker urlSafetyChecker;
  private final ApplicationEventPublisher events;
  private final AuditLogService auditLogService;
  private final BlockedDomainService blockedDomainService;
  private final TransactionTemplate tx;
  private final long quotaPerUser;

  public LinkCreationService(
      LinkRepository repository,
      LinkOgMetadataRepository ogMetadataRepository,
      ShortCodeGenerator generator,
      MeterRegistry meterRegistry,
      UrlSafetyChecker urlSafetyChecker,
      ApplicationEventPublisher events,
      AuditLogService auditLogService,
      BlockedDomainService blockedDomainService,
      PlatformTransactionManager transactionManager,
      @Value("${short-link.link-quota.authenticated:200}") long quotaPerUser) {
    this.repository = repository;
    this.ogMetadataRepository = ogMetadataRepository;
    this.generator = generator;
    this.meterRegistry = meterRegistry;
    this.urlSafetyChecker = urlSafetyChecker;
    this.events = events;
    this.auditLogService = auditLogService;
    this.blockedDomainService = blockedDomainService;
    this.tx = new TransactionTemplate(transactionManager);
    this.quotaPerUser = quotaPerUser;
  }

  public LinkCreated create(
      String url, Long userId, String customCode, Instant requestedExpiresAt) {
    return create(url, userId, customCode, requestedExpiresAt, true);
  }

  /**
   * @param deduplicate true 면 같은 owner + 같은 URL 의 기존 link 재사용 (일반 단축 흐름). Campaign batch 가 같은
   *     destination 으로 여러 묶음을 만들 때 batch:link UNIQUE 제약과 충돌하므로 false 로 호출 — 각 batch 가 자기 단축 코드를 갖도록
   *     한다.
   */
  public LinkCreated create(
      String url, Long userId, String customCode, Instant requestedExpiresAt, boolean deduplicate) {
    // Pre-transaction validation: blocked-domain lookup is cheap and Safe Browsing is an outbound
    // HTTP call. Both used to run inside the @Transactional boundary, which held a JDBC connection
    // for the duration of the HTTP round trip — pool starvation under load. Run them first so the
    // DB transaction only spans the actual persistence work.
    if (blockedDomainService.isBlocked(url)) {
      meterRegistry.counter("short_link.created", "result", "blocked_domain").increment();
      throw new LinkException(LinkErrorCode.MALICIOUS_URL, LinkUrlHasher.sha256Prefix(url));
    }
    if (!urlSafetyChecker.isSafe(url)) {
      throw new LinkException(LinkErrorCode.MALICIOUS_URL, LinkUrlHasher.sha256Prefix(url));
    }

    boolean authenticated = userId != null;
    String code = authenticated ? customCode : null;
    Instant expiresAt = authenticated ? requestedExpiresAt : Instant.now().plus(ANONYMOUS_TTL);

    if (code != null && ReservedShortCodes.isReserved(code)) {
      throw new LinkException(LinkErrorCode.RESERVED_SHORT_CODE, code);
    }

    return tx.execute(status -> persist(url, userId, code, expiresAt, deduplicate, authenticated));
  }

  private LinkCreated persist(
      String url,
      Long userId,
      String code,
      Instant expiresAt,
      boolean deduplicate,
      boolean authenticated) {
    if (deduplicate && authenticated && code == null) {
      Optional<LinkEntity> existing = repository.findFirstByUserIdAndOriginalUrl(userId, url);
      if (existing.isPresent()) {
        LinkEntity link = existing.get();
        if (!link.isExpired(Instant.now())) {
          recordCreated(true, false, "deduplicated");
          return new LinkCreated(link.getShortCode());
        }
      }
    }

    if (authenticated) {
      long current = repository.countByUserId(userId);
      if (current >= quotaPerUser) {
        throw new LinkException(LinkErrorCode.LINK_QUOTA_EXCEEDED, quotaPerUser)
            .with("limit", quotaPerUser);
      }
    }

    if (code != null) {
      try {
        LinkEntity saved = saveWithCode(url, code, userId, expiresAt, authenticated);
        recordCreated(true, true, "ok");
        publishCreated(saved, userId, true);
        return new LinkCreated(saved.getShortCode(), saved.getClaimToken());
      } catch (DataIntegrityViolationException e) {
        throw new LinkException(LinkErrorCode.DUPLICATE_SHORT_CODE, code);
      }
    }

    for (int i = 0; i < MAX_ATTEMPTS; i++) {
      String generated = generator.generate();
      if (ReservedShortCodes.isReserved(generated)) {
        continue;
      }
      try {
        LinkEntity saved = saveWithCode(url, generated, userId, expiresAt, authenticated);
        recordCreated(authenticated, false, "ok");
        publishCreated(saved, userId, false);
        return new LinkCreated(saved.getShortCode(), saved.getClaimToken());
      } catch (DataIntegrityViolationException ignored) {
      }
    }
    throw new LinkException(LinkErrorCode.SHORT_CODE_EXHAUSTED);
  }

  private LinkEntity saveWithCode(
      String url, String code, Long userId, Instant expiresAt, boolean authenticated) {
    LinkEntity entity = new LinkEntity(url, code, userId, expiresAt);
    attachClaimTokenIfAnonymous(entity, authenticated);
    LinkEntity saved = repository.save(entity);
    ogMetadataRepository.save(new LinkOgMetadataEntity(saved.getId()));
    return saved;
  }

  private void publishCreated(LinkEntity saved, Long userId, boolean custom) {
    events.publishEvent(new LinkOgFetchRequested(saved.getShortCode(), saved.getOriginalUrl()));
    auditLogService.record(
        AuditAction.LINK_CREATED, "link", saved.getShortCode(), userId, Map.of("custom", custom));
  }

  private static void attachClaimTokenIfAnonymous(LinkEntity entity, boolean authenticated) {
    if (authenticated) return;
    byte[] bytes = new byte[16];
    CLAIM_RANDOM.nextBytes(bytes);
    StringBuilder hex = new StringBuilder(32);
    for (byte b : bytes) hex.append(String.format("%02x", b));
    entity.setClaimToken(hex.toString());
  }

  private void recordCreated(boolean authenticated, boolean custom, String result) {
    meterRegistry
        .counter(
            "short_link.created",
            "authenticated",
            String.valueOf(authenticated),
            "custom",
            String.valueOf(custom),
            "result",
            result)
        .increment();
  }
}
