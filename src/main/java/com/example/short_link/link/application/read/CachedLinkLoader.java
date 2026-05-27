package com.example.short_link.link.application.read;

import com.example.short_link.link.application.dto.CachedLink;
import com.example.short_link.link.destination.domain.LinkDestinationEntity;
import com.example.short_link.link.destination.domain.repository.LinkDestinationRepository;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the Redis-backed {@code "link"} cache for redirect-path lookups. Lives in a separate bean so
 * the {@link Cacheable} proxy actually intercepts the call — when this method sat next to {@code
 * findActiveLink} on {@link LinkLookupQueryService}, self-invocation bypassed AOP and every
 * redirect hit MySQL.
 */
@Component
@RequiredArgsConstructor
public class CachedLinkLoader {

  private final LinkRepository repository;
  private final LinkDestinationRepository destinationRepository;

  @Cacheable("link")
  @Transactional(readOnly = true)
  public CachedLink loadByShortCode(ShortCode shortCode) {
    LinkRepository.CachedLinkRow link =
        repository
            .findCachedLinkRowByShortCode(shortCode)
            .orElseThrow(() -> new LinkException(LinkErrorCode.LINK_NOT_FOUND, shortCode));
    List<CachedLink.Variant> variants =
        destinationRepository.findAllByLinkIdOrderByIdAsc(link.getId()).stream()
            .map(CachedLinkLoader::toVariant)
            .toList();
    return new CachedLink(
        new LinkId(link.getId()),
        link.getShortCode(),
        link.getUserId(),
        link.getOriginalUrl(),
        link.getExpiresAt(),
        link.getOgTitle(),
        link.getOgDescription(),
        link.getOgImage(),
        link.getBlockedCountries(),
        Boolean.TRUE.equals(link.getPasswordRequired()),
        link.getMaxViews(),
        link.getExpiredMessage(),
        variants);
  }

  private static CachedLink.Variant toVariant(LinkDestinationEntity d) {
    return new CachedLink.Variant(
        d.getId(),
        d.getUrl(),
        d.getWeight(),
        d.isEnabled(),
        d.getCountryCode(),
        d.getDeviceClass(),
        d.getOs());
  }
}
