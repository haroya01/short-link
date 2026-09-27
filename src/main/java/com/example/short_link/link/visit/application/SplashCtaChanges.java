package com.example.short_link.link.visit.application;

import com.example.short_link.link.application.LinkCacheEviction;
import com.example.short_link.link.visit.domain.repository.LinkVisitOptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SplashCtaChanges {

  private final LinkVisitOptionRepository options;
  private final LinkCacheEviction linkCacheEviction;

  @Transactional(readOnly = true)
  public void ctaChanged(Long ctaId) {
    options.findShortCodesUsingSplashCta(ctaId).forEach(linkCacheEviction::evictAfterCommit);
  }
}
