package com.example.short_link.link.presentation.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.hibernate.validator.constraints.URL;

public record UpdateLinkRequest(
    @URL
        @Pattern(regexp = "^https?://.*", message = "URL must use http or https")
        @Size(min = 1, max = 2048)
        String originalUrl,
    Instant expiresAt,
    @Size(max = 280) String note,
    @Size(max = 500) String expiredMessage,
    Boolean clearExpiresAt) {
  public UpdateLinkRequest {
    clearExpiresAt = Boolean.TRUE.equals(clearExpiresAt);
  }

  public UpdateLinkRequest(
      String originalUrl, Instant expiresAt, String note, String expiredMessage) {
    this(originalUrl, expiresAt, note, expiredMessage, false);
  }

  @AssertTrue(message = "expiresAt and clearExpiresAt cannot be set together")
  public boolean isExpiryChangeConsistent() {
    return !clearExpiresAt || expiresAt == null;
  }
}
