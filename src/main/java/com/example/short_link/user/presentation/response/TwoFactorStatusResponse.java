package com.example.short_link.user.presentation.response;

import com.example.short_link.user.application.twofactor.TwoFactorService.Status;
import java.time.Instant;

public record TwoFactorStatusResponse(boolean enabled, Instant lastUsedAt) {

  public static TwoFactorStatusResponse from(Status s) {
    return new TwoFactorStatusResponse(s.enabled(), s.lastUsedAt());
  }
}
