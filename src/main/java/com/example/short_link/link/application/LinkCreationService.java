package com.example.short_link.link.application;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LinkCreationService {

  private static final int MAX_ATTEMPTS = 5;
  private static final Duration ANONYMOUS_TTL = Duration.ofDays(1);

  private final LinkRepository repository;
  private final ShortCodeGenerator generator;
  private final MeterRegistry meterRegistry;

  public LinkCreated create(
      String url, Long userId, String customCode, Instant requestedExpiresAt) {
    boolean authenticated = userId != null;
    String code = authenticated ? customCode : null;
    Instant expiresAt = authenticated ? requestedExpiresAt : Instant.now().plus(ANONYMOUS_TTL);

    if (code != null) {
      try {
        LinkEntity saved = repository.save(new LinkEntity(url, code, userId, expiresAt));
        recordCreated(true, true);
        return new LinkCreated(saved.getShortCode());
      } catch (DataIntegrityViolationException e) {
        throw new DuplicateShortCodeException(code);
      }
    }

    for (int i = 0; i < MAX_ATTEMPTS; i++) {
      String generated = generator.generate();
      try {
        LinkEntity saved = repository.save(new LinkEntity(url, generated, userId, expiresAt));
        recordCreated(authenticated, false);
        return new LinkCreated(saved.getShortCode());
      } catch (DataIntegrityViolationException ignored) {
      }
    }
    throw new ShortCodeGenerationException();
  }

  private void recordCreated(boolean authenticated, boolean custom) {
    meterRegistry
        .counter(
            "short_link.created",
            "authenticated",
            String.valueOf(authenticated),
            "custom",
            String.valueOf(custom))
        .increment();
  }
}
