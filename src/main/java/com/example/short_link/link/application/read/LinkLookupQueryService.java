package com.example.short_link.link.application.read;

import com.example.short_link.link.application.dto.CachedLink;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
import com.example.short_link.link.stats.domain.repository.ClickTotalsReadRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LinkLookupQueryService {

  private final CachedLinkLoader cachedLinkLoader;
  private final LinkRepository repository;
  private final ClickTotalsReadRepository clickTotalsRepository;
  private final MeterRegistry meterRegistry;

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
      cached = cachedLinkLoader.loadByShortCode(shortCode);
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
