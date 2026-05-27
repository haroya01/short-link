package com.example.short_link.link.application.write;

import com.example.short_link.common.audit.AuditAction;
import com.example.short_link.common.audit.AuditLogService;
import com.example.short_link.link.application.ShortCodeGenerator;
import com.example.short_link.link.application.dto.LinkCreated;
import com.example.short_link.link.application.helper.ReservedShortCodes;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
import com.example.short_link.link.og.application.dto.LinkOgFetchRequested;
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
public class CreateLinkUseCase {

  private static final int MAX_ATTEMPTS = 5;
  private static final Duration ANONYMOUS_TTL = Duration.ofDays(1);
  private static final SecureRandom CLAIM_RANDOM = new SecureRandom();

  private final LinkRepository repository;
  private final ShortCodeGenerator generator;
  private final MeterRegistry meterRegistry;
  private final ApplicationEventPublisher events;
  private final AuditLogService auditLogService;
  private final CreateLinkValidator validator;
  private final LinkSidecarPersister sidecarPersister;
  private final TransactionTemplate tx;
  private final long quotaPerUser;

  public CreateLinkUseCase(
      LinkRepository repository,
      ShortCodeGenerator generator,
      MeterRegistry meterRegistry,
      ApplicationEventPublisher events,
      AuditLogService auditLogService,
      CreateLinkValidator validator,
      LinkSidecarPersister sidecarPersister,
      PlatformTransactionManager transactionManager,
      @Value("${short-link.link-quota.authenticated:200}") long quotaPerUser) {
    this.repository = repository;
    this.generator = generator;
    this.meterRegistry = meterRegistry;
    this.events = events;
    this.auditLogService = auditLogService;
    this.validator = validator;
    this.sidecarPersister = sidecarPersister;
    this.tx = new TransactionTemplate(transactionManager);
    this.quotaPerUser = quotaPerUser;
  }

  public LinkCreated execute(CreateLinkCommand command) {
    String url = command.url();
    validator.validateUrl(url);

    boolean authenticated = command.userId() != null;
    String code = authenticated ? command.customCode() : null;
    Instant expiresAt = authenticated ? command.expiresAt() : Instant.now().plus(ANONYMOUS_TTL);

    validator.rejectIfReserved(code);

    return tx.execute(
        status ->
            persist(url, command.userId(), code, expiresAt, command.deduplicate(), authenticated));
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
    sidecarPersister.persistAll(saved);
    return saved;
  }

  private void publishCreated(LinkEntity saved, Long userId, boolean custom) {
    events.publishEvent(new LinkOgFetchRequested(saved.getShortCode(), saved.getOriginalUrl()));
    auditLogService.record(
        AuditAction.LINK_CREATED,
        "link",
        saved.getShortCode().value(),
        userId,
        Map.of("custom", custom));
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
