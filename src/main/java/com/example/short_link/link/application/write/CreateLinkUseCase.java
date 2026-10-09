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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
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
  private final LinkDefaultsWriter defaultsWriter;
  private final DedicatedLinks dedicatedLinks;
  private final PasswordEncoder passwordEncoder;
  private final TransactionTemplate tx;
  private final long quotaPerUser;

  public CreateLinkUseCase(
      LinkRepository repository,
      ShortCodeGenerator generator,
      MeterRegistry meterRegistry,
      ApplicationEventPublisher events,
      AuditLogService auditLogService,
      CreateLinkValidator validator,
      LinkDefaultsWriter defaultsWriter,
      DedicatedLinks dedicatedLinks,
      @Qualifier("linkPasswordEncoder") PasswordEncoder passwordEncoder,
      PlatformTransactionManager transactionManager,
      @Value("${short-link.link-quota.authenticated:200}") long quotaPerUser) {
    this.repository = repository;
    this.generator = generator;
    this.meterRegistry = meterRegistry;
    this.events = events;
    this.auditLogService = auditLogService;
    this.validator = validator;
    this.defaultsWriter = defaultsWriter;
    this.dedicatedLinks = dedicatedLinks;
    this.passwordEncoder = passwordEncoder;
    this.tx = new TransactionTemplate(transactionManager);
    this.quotaPerUser = quotaPerUser;
  }

  public LinkCreated execute(CreateLinkCommand command) {
    String url = command.url();
    validator.validateUrl(url, command.allowSelfHost());

    boolean authenticated = command.userId() != null;
    String code = authenticated ? command.customCode() : null;
    Instant expiresAt = authenticated ? command.expiresAt() : Instant.now().plus(ANONYMOUS_TTL);

    validator.rejectIfReserved(code);
    // bcrypt 는 수십 ms 가 걸려서 커넥션을 잡기 전에 끝낸다.
    String passwordHash =
        authenticated && command.password() != null && !command.password().isBlank()
            ? passwordEncoder.encode(command.password())
            : null;

    // 실패한 INSERT 는 트랜잭션을 rollback-only 로 만들어서 코드마다 트랜잭션을 따로 연다.
    for (int i = 0; i < MAX_ATTEMPTS; i++) {
      String candidate = code != null ? code : generator.generate();
      if (code == null && ReservedShortCodes.isReserved(candidate)) {
        continue;
      }
      try {
        return tx.execute(
            status ->
                persist(
                    url,
                    command.userId(),
                    candidate,
                    code != null,
                    expiresAt,
                    command.deduplicate(),
                    authenticated,
                    passwordHash));
      } catch (DataIntegrityViolationException collision) {
        if (code != null) {
          throw new LinkException(LinkErrorCode.DUPLICATE_SHORT_CODE, code);
        }
      }
    }
    throw new LinkException(LinkErrorCode.SHORT_CODE_EXHAUSTED);
  }

  private LinkCreated persist(
      String url,
      Long userId,
      String code,
      boolean custom,
      Instant expiresAt,
      boolean deduplicate,
      boolean authenticated,
      String passwordHash) {
    // 비밀번호·만료를 건 요청이 이미 공유된 기존 링크를 돌려받으면 안 된다.
    if (deduplicate && authenticated && !custom && passwordHash == null && expiresAt == null) {
      Optional<LinkEntity> existing = reusableLink(userId, url);
      if (existing.isPresent()) {
        LinkEntity link = existing.get();
        recordCreated(true, false, "deduplicated");
        return new LinkCreated(link.getShortCode(), null, link.hasPassword());
      }
    }

    if (authenticated) {
      long current = repository.countByUserId(userId);
      if (current >= quotaPerUser) {
        throw new LinkException(LinkErrorCode.LINK_QUOTA_EXCEEDED, quotaPerUser)
            .with("limit", quotaPerUser);
      }
    }

    LinkEntity saved = saveWithCode(url, code, userId, expiresAt, authenticated, passwordHash);
    recordCreated(authenticated, custom, "ok");
    publishCreated(saved, userId, custom);
    return new LinkCreated(saved.getShortCode(), saved.getClaimToken(), saved.hasPassword());
  }

  private Optional<LinkEntity> reusableLink(Long userId, String url) {
    return repository.findUnrestrictedByUserIdAndOriginalUrl(userId, url).stream()
        .filter(link -> !dedicatedLinks.isDedicated(link.linkId()))
        .findFirst();
  }

  private LinkEntity saveWithCode(
      String url,
      String code,
      Long userId,
      Instant expiresAt,
      boolean authenticated,
      String passwordHash) {
    LinkEntity entity = new LinkEntity(url, code, userId, expiresAt);
    attachClaimTokenIfAnonymous(entity, authenticated);
    entity.setPasswordHash(passwordHash);
    LinkEntity saved = repository.save(entity);
    defaultsWriter.initialize(saved.linkId(), passwordHash);
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
