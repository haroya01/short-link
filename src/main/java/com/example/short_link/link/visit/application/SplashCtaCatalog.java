package com.example.short_link.link.visit.application;

import java.util.Optional;

/**
 * Implemented by the CTA context, which already depends on links — the reverse import would cycle.
 */
public interface SplashCtaCatalog {

  Optional<SplashCta> findOwned(Long ctaId, Long ownerId);
}
