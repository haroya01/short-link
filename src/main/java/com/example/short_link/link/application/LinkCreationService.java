package com.example.short_link.link.application;

import com.example.short_link.admin.application.BlockedDomainService;
import com.example.short_link.common.audit.AuditAction;
import com.example.short_link.common.audit.AuditLogService;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
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
import org.springframework.transaction.annotation.Transactional;

@Service
public class LinkCreationService {

  private static final int MAX_ATTEMPTS = 5;
  private static final Duration ANONYMOUS_TTL = Duration.ofDays(1);

  private final LinkRepository repository;
  private final ShortCodeGenerator generator;
  private final MeterRegistry meterRegistry;
  private final UrlSafetyChecker urlSafetyChecker;
  private final ApplicationEventPublisher events;
  private final AuditLogService auditLogService;
  private final BlockedDomainService blockedDomainService;
  private final long quotaPerUser;

  public LinkCreationService(
      LinkRepository repository,
      ShortCodeGenerator generator,
      MeterRegistry meterRegistry,
      UrlSafetyChecker urlSafetyChecker,
      ApplicationEventPublisher events,
      AuditLogService auditLogService,
      BlockedDomainService blockedDomainService,
      @Value("${short-link.link-quota.authenticated:200}") long quotaPerUser) {
    this.repository = repository;
    this.generator = generator;
    this.meterRegistry = meterRegistry;
    this.urlSafetyChecker = urlSafetyChecker;
    this.events = events;
    this.auditLogService = auditLogService;
    this.blockedDomainService = blockedDomainService;
    this.quotaPerUser = quotaPerUser;
  }

  @Transactional
  public LinkCreated create(
      String url, Long userId, String customCode, Instant requestedExpiresAt) {
    if (blockedDomainService.isBlocked(url)) {
      meterRegistry.counter("short_link.created", "result", "blocked_domain").increment();
      throw new MaliciousUrlException(url);
    }
    if (!urlSafetyChecker.isSafe(url)) {
      throw new MaliciousUrlException(url);
    }
    boolean authenticated = userId != null;
    String code = authenticated ? customCode : null;
    Instant expiresAt = authenticated ? requestedExpiresAt : Instant.now().plus(ANONYMOUS_TTL);

    if (code != null && ReservedShortCodes.isReserved(code)) {
      throw new ReservedShortCodeException(code);
    }

    if (authenticated && code == null) {
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
        throw new LinkQuotaExceededException(quotaPerUser);
      }
    }

    if (code != null) {
      try {
        LinkEntity entity = new LinkEntity(url, code, userId, expiresAt);
        attachClaimTokenIfAnonymous(entity, authenticated);
        LinkEntity saved = repository.save(entity);
        recordCreated(true, true, "ok");
        events.publishEvent(new LinkOgFetchRequested(saved.getShortCode(), url));
        auditLogService.record(
            AuditAction.LINK_CREATED, "link", saved.getShortCode(), userId, Map.of("custom", true));
        return new LinkCreated(saved.getShortCode(), saved.getClaimToken());
      } catch (DataIntegrityViolationException e) {
        throw new DuplicateShortCodeException(code);
      }
    }

    for (int i = 0; i < MAX_ATTEMPTS; i++) {
      String generated = generator.generate();
      if (ReservedShortCodes.isReserved(generated)) {
        continue;
      }
      try {
        LinkEntity entity = new LinkEntity(url, generated, userId, expiresAt);
        attachClaimTokenIfAnonymous(entity, authenticated);
        LinkEntity saved = repository.save(entity);
        recordCreated(authenticated, false, "ok");
        events.publishEvent(new LinkOgFetchRequested(saved.getShortCode(), url));
        auditLogService.record(
            AuditAction.LINK_CREATED,
            "link",
            saved.getShortCode(),
            userId,
            Map.of("custom", false));
        return new LinkCreated(saved.getShortCode(), saved.getClaimToken());
      } catch (DataIntegrityViolationException ignored) {
      }
    }
    throw new ShortCodeGenerationException();
  }

  private static final SecureRandom CLAIM_RANDOM = new SecureRandom();

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
