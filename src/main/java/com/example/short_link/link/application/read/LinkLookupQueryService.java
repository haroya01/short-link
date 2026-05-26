package com.example.short_link.link.application.read;

import com.example.short_link.link.application.dto.CachedLink;
import com.example.short_link.link.destination.domain.LinkDestinationEntity;
import com.example.short_link.link.destination.domain.repository.LinkDestinationRepository;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
import com.example.short_link.link.stats.domain.repository.ClickTotalsReadRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LinkLookupQueryService {

  private final LinkRepository repository;
  private final LinkDestinationRepository destinationRepository;
  private final ClickTotalsReadRepository clickTotalsRepository;
  private final MeterRegistry meterRegistry;

  @Cacheable("link")
  @Transactional(readOnly = true)
  private CachedLink loadByShortCode(ShortCode shortCode) {
    LinkEntity link =
        repository
            .findByShortCode(shortCode)
            .orElseThrow(() -> new LinkException(LinkErrorCode.LINK_NOT_FOUND, shortCode));
    List<CachedLink.Variant> variants =
        destinationRepository.findAllByLinkIdOrderByIdAsc(link.linkId()).stream()
            .map(LinkLookupQueryService::toVariant)
            .toList();
    return new CachedLink(
        link.linkId(),
        link.getUserId(),
        link.getOriginalUrl(),
        link.getExpiresAt(),
        link.getOgTitle(),
        link.getOgDescription(),
        link.getOgImage(),
        link.getBlockedCountries(),
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

  /** SSE / OG card 등 entity 가 직접 필요한 controller 용. 못 찾으면 empty. */
  @Transactional(readOnly = true)
  public Optional<LinkEntity> findEntity(ShortCode shortCode) {
    return repository.findByShortCode(shortCode);
  }

  /** OG card 의 click count 배지용. bot 제외 휴먼 클릭만. */
  @Transactional(readOnly = true)
  public long countHumanClicks(LinkId linkId) {
    return clickTotalsRepository.countHumanByLinkId(linkId.value());
  }

  public CachedLink findActiveLink(ShortCode shortCode) {
    CachedLink cached;
    try {
      cached = loadByShortCode(shortCode);
    } catch (LinkException e) {
      meterRegistry.counter("short_link.lookup", "result", "not_found").increment();
      throw e;
    }
    if (cached.isExpired(Instant.now())) {
      meterRegistry.counter("short_link.lookup", "result", "expired").increment();
      throw new LinkException(LinkErrorCode.LINK_EXPIRED, shortCode);
    }
    meterRegistry.counter("short_link.lookup", "result", "redirected").increment();
    return cached;
  }
}
