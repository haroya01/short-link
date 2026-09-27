package com.example.short_link.link.visit.application;

import com.example.short_link.link.application.LinkCacheEviction;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
import com.example.short_link.link.visit.domain.LinkVisitOptionEntity;
import com.example.short_link.link.visit.domain.repository.LinkVisitOptionRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LinkVisitOptionService {

  private final LinkRepository links;
  private final LinkVisitOptionRepository options;
  private final LinkCacheEviction linkCacheEviction;
  private final SplashCtaCatalog ctaCatalog;

  @Transactional
  public LinkVisitOptionEntity update(
      Long userId,
      ShortCode shortCode,
      Boolean openInBrowser,
      SplashChange splash,
      Instant opensAt,
      boolean clearOpensAt) {
    LinkEntity link =
        links
            .findByShortCode(shortCode)
            .orElseThrow(() -> new LinkException(LinkErrorCode.LINK_NOT_FOUND, shortCode));
    if (!link.isOwnedBy(userId)) {
      throw new LinkException(LinkErrorCode.LINK_NOT_OWNED, shortCode);
    }
    LinkVisitOptionEntity option =
        options.findById(link.getId()).orElseGet(() -> new LinkVisitOptionEntity(link.linkId()));
    if (openInBrowser != null) {
      option.changeOpenInBrowser(openInBrowser);
    }
    if (splash != null) {
      Long ctaId = splash.ctaId();
      if (ctaId != null && ctaCatalog.findOwned(ctaId, userId).isEmpty()) {
        throw new LinkException(LinkErrorCode.SPLASH_CTA_NOT_FOUND, ctaId);
      }
      option.changeSplash(
          splash.enabled(),
          splash.message(),
          splash.seconds() == null ? 3 : splash.seconds(),
          ctaId);
    }
    if (clearOpensAt) {
      option.changeOpensAt(null);
    } else if (opensAt != null) {
      if (link.getExpiresAt() != null && !opensAt.isBefore(link.getExpiresAt())) {
        throw new LinkException(LinkErrorCode.OPENS_AFTER_EXPIRY, shortCode);
      }
      option.changeOpensAt(opensAt);
    }
    linkCacheEviction.evictAfterCommit(shortCode);
    return options.save(option);
  }
}
