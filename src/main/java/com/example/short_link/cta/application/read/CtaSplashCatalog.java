package com.example.short_link.cta.application.read;

import com.example.short_link.cta.domain.repository.CtaRepository;
import com.example.short_link.link.application.ShortLinkUrlBuilder;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.visit.application.SplashCta;
import com.example.short_link.link.visit.application.SplashCtaCatalog;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class CtaSplashCatalog implements SplashCtaCatalog {

  private final CtaRepository ctas;
  private final ShortLinkUrlBuilder urls;

  @Override
  public Optional<SplashCta> findOwned(Long ctaId, Long ownerId) {
    return ctas.findById(ctaId)
        .filter(cta -> cta.isOwnedBy(ownerId) && !cta.isDeleted())
        .map(
            cta ->
                new SplashCta(
                    cta.getId(),
                    cta.getLabel(),
                    cta.getTrackedShortCode() != null
                        ? urls.build(ShortCode.of(cta.getTrackedShortCode()))
                        : cta.getUrl()));
  }
}
