package com.example.short_link.link.application;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lets the link owner override the auto-scraped OG metadata. Override values are returned to social
 * crawlers in preference to the scraped ones; pass {@code null} (or blank) to clear an override and
 * fall back to the scraped value.
 */
@Service
@RequiredArgsConstructor
public class OgOverrideService {

  private final LinkRepository repository;

  @Transactional
  @Caching(
      evict = {
        @CacheEvict(value = "link", key = "#shortCode"),
        @CacheEvict(value = "public-profile", allEntries = true)
      })
  public OgOverrideResult update(
      Long userId, String shortCode, String title, String description, String image) {
    LinkEntity link =
        repository
            .findByShortCode(shortCode)
            .orElseThrow(() -> new LinkNotFoundException(shortCode));
    if (!link.isOwnedBy(userId)) {
      throw new LinkNotOwnedException(shortCode);
    }
    link.changeOgOverride(title, description, image);
    return new OgOverrideResult(
        link.getShortCode(),
        link.getOgTitleOverride(),
        link.getOgDescriptionOverride(),
        link.getOgImageOverride());
  }
}
