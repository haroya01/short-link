package com.example.short_link.link.og.application;

import com.example.short_link.common.cache.ProfileCacheInvalidator;
import com.example.short_link.link.application.LinkCacheEviction;
import com.example.short_link.link.application.write.LinkOwnership;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.og.application.dto.OgOverrideResult;
import com.example.short_link.link.og.domain.LinkOgMetadataEntity;
import com.example.short_link.link.og.domain.repository.LinkOgMetadataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OgOverrideService {

  private final LinkOwnership ownership;
  private final LinkOgMetadataRepository ogMetadataRepository;
  private final ProfileCacheInvalidator cacheEviction;
  private final LinkCacheEviction linkCacheEviction;

  @Transactional
  public OgOverrideResult update(
      Long userId, ShortCode shortCode, String title, String description, String image) {
    LinkEntity link = ownership.requireOwned(userId, shortCode);
    link.changeOgOverride(title, description, image);
    LinkOgMetadataEntity ogMeta =
        ogMetadataRepository
            .findById(link.getId())
            .orElseGet(() -> new LinkOgMetadataEntity(link.linkId()));
    ogMeta.changeOverride(title, description, image);
    ogMetadataRepository.save(ogMeta);
    cacheEviction.evictByUserId(userId);
    linkCacheEviction.evictAfterCommit(shortCode);
    return new OgOverrideResult(
        link.getShortCode(),
        link.getOgTitleOverride(),
        link.getOgDescriptionOverride(),
        link.getOgImageOverride());
  }
}
