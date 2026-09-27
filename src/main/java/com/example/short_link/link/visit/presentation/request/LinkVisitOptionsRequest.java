package com.example.short_link.link.visit.presentation.request;

import com.example.short_link.link.visit.application.SplashChange;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record LinkVisitOptionsRequest(
    Boolean openInBrowser, @Valid Splash splash, Instant opensAt, Boolean clearOpensAt) {

  @AssertTrue(message = "opensAt and clearOpensAt cannot be set together")
  public boolean isOpeningChangeConsistent() {
    return !Boolean.TRUE.equals(clearOpensAt) || opensAt == null;
  }

  public record Splash(
      boolean enabled,
      @Size(max = 280) String message,
      @Min(1) @Max(5) Integer seconds,
      Long ctaId) {

    @AssertTrue(message = "a splash needs a message")
    public boolean isMessagePresentWhenEnabled() {
      return !enabled || (message != null && !message.isBlank());
    }

    public SplashChange toChange() {
      return new SplashChange(enabled, message, seconds, ctaId);
    }
  }
}
