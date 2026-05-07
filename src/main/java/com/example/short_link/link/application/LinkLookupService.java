package com.example.short_link.link.application;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LinkLookupService {

  private final LinkRepository repository;
  private final MeterRegistry meterRegistry;

  @Cacheable("link")
  @Transactional(readOnly = true)
  public CachedLink loadByShortCode(String shortCode) {
    LinkEntity link =
        repository
            .findByShortCode(shortCode)
            .orElseThrow(() -> new LinkNotFoundException(shortCode));
    return new CachedLink(link.getId(), link.getOriginalUrl(), link.getExpiresAt());
  }

  public String findActiveOriginalUrl(String shortCode) {
    return findActiveLink(shortCode).originalUrl();
  }

  public CachedLink findActiveLink(String shortCode) {
    CachedLink cached;
    try {
      cached = loadByShortCode(shortCode);
    } catch (LinkNotFoundException e) {
      meterRegistry.counter("short_link.lookup", "result", "not_found").increment();
      throw e;
    }
    if (cached.isExpired(Instant.now())) {
      meterRegistry.counter("short_link.lookup", "result", "expired").increment();
      throw new LinkExpiredException(shortCode);
    }
    meterRegistry.counter("short_link.lookup", "result", "redirected").increment();
    return cached;
  }
}
