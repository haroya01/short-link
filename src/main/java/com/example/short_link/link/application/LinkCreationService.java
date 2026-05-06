package com.example.short_link.link.application;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
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

  public LinkCreated create(String url, Long userId) {
    Instant expiresAt = userId == null ? Instant.now().plus(ANONYMOUS_TTL) : null;
    for (int i = 0; i < MAX_ATTEMPTS; i++) {
      String code = generator.generate();
      try {
        LinkEntity saved = repository.save(new LinkEntity(url, code, userId, expiresAt));
        return new LinkCreated(saved.getShortCode());
      } catch (DataIntegrityViolationException ignored) {
      }
    }
    throw new ShortCodeGenerationException();
  }
}
