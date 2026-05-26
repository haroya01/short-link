package com.example.short_link.link.og.application;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
import com.example.short_link.link.og.application.dto.OgOverrideResult;
import com.example.short_link.link.og.domain.LinkOgMetadataEntity;
import com.example.short_link.link.og.domain.repository.LinkOgMetadataRepository;
import com.example.short_link.profile.application.ProfileCacheEviction;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
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
  private final LinkOgMetadataRepository ogMetadataRepository;
  private final ProfileCacheEviction cacheEviction;

  @Transactional
  @CacheEvict(value = "link", key = "#shortCode")
  public OgOverrideResult update(
      Long userId, ShortCode shortCode, String title, String description, String image) {
    LinkEntity link =
        repository
            .findByShortCode(shortCode)
            .orElseThrow(() -> new LinkException(LinkErrorCode.LINK_NOT_FOUND, shortCode));
    if (!link.isOwnedBy(userId)) {
      throw new LinkException(LinkErrorCode.LINK_NOT_OWNED, shortCode);
    }
    link.changeOgOverride(title, description, image);
    LinkOgMetadataEntity ogMeta =
        ogMetadataRepository
            .findById(link.getId())
            .orElseGet(() -> new LinkOgMetadataEntity(link.linkId()));
    ogMeta.changeOverride(title, description, image);
    ogMetadataRepository.save(ogMeta);
    cacheEviction.evictByUserId(userId);
    return new OgOverrideResult(
        link.getShortCode(),
        link.getOgTitleOverride(),
        link.getOgDescriptionOverride(),
        link.getOgImageOverride());
  }
}
