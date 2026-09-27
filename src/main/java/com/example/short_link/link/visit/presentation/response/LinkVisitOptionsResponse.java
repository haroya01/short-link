package com.example.short_link.link.visit.presentation.response;

import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.visit.domain.LinkVisitOptionEntity;

public record LinkVisitOptionsResponse(ShortCode shortCode, boolean openInBrowser, Splash splash) {

  public record Splash(boolean enabled, String message, int seconds, Long ctaId) {}

  public static LinkVisitOptionsResponse from(ShortCode shortCode, LinkVisitOptionEntity option) {
    return new LinkVisitOptionsResponse(
        shortCode,
        option.isOpenInBrowser(),
        new Splash(
            option.isSplashEnabled(),
            option.getSplashMessage(),
            option.getSplashSeconds(),
            option.getSplashCtaId()));
  }
}
