package com.example.short_link.link.application;

import com.example.short_link.link.domain.ClickEventRepository;
import com.example.short_link.link.domain.LinkDestinationEntity;
import com.example.short_link.link.domain.LinkDestinationRepository;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
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
public class LinkLookupService {

  private final LinkRepository repository;
  private final LinkDestinationRepository destinationRepository;
  private final ClickEventRepository clickEventRepository;
  private final MeterRegistry meterRegistry;

  @Cacheable("link")
  @Transactional(readOnly = true)
  public CachedLink loadByShortCode(String shortCode) {
    LinkEntity link =
        repository
            .findByShortCode(shortCode)
            .orElseThrow(() -> new LinkNotFoundException(shortCode));
    List<CachedLink.Variant> variants =
        destinationRepository.findAllByLinkIdOrderByIdAsc(link.getId()).stream()
            .map(LinkLookupService::toVariant)
            .toList();
    return new CachedLink(
        link.getId(),
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

  public String findActiveOriginalUrl(String shortCode) {
    return findActiveLink(shortCode).originalUrl();
  }

  /** SSE / OG card 등 entity 가 직접 필요한 controller 용. 못 찾으면 empty. */
  @Transactional(readOnly = true)
  public Optional<LinkEntity> findEntity(String shortCode) {
    return repository.findByShortCode(shortCode);
  }

  /** OG card 의 click count 배지용. bot 제외 휴먼 클릭만. */
  @Transactional(readOnly = true)
  public long countHumanClicks(Long linkId) {
    return clickEventRepository.countHumanByLinkId(linkId);
  }

  /**
   * view limit 체크 + viewCount 증분 atomically. 반환값 > 0 = 증분 됐음, 0 = limit 도달 (호출자가 expire 처리).
   * redirect 핸들러용.
   */
  public int incrementViewCountIfBelowLimit(Long linkId) {
    return repository.incrementViewCountIfBelowLimit(linkId);
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
