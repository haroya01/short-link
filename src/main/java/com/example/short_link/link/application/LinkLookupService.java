package com.example.short_link.link.application;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LinkLookupService {

  private final LinkRepository repository;

  @Cacheable("link")
  @Transactional(readOnly = true)
  public CachedLink loadByShortCode(String shortCode) {
    LinkEntity link =
        repository
            .findByShortCode(shortCode)
            .orElseThrow(() -> new LinkNotFoundException(shortCode));
    return new CachedLink(link.getOriginalUrl(), link.getExpiresAt());
  }

  public String findActiveOriginalUrl(String shortCode) {
    CachedLink cached = loadByShortCode(shortCode);
    if (cached.isExpired(Instant.now())) {
      throw new LinkExpiredException(shortCode);
    }
    return cached.originalUrl();
  }
}
