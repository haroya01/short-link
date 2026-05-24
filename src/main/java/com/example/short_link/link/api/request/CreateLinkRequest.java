package com.example.short_link.link.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.hibernate.validator.constraints.URL;

public record CreateLinkRequest(
    @NotBlank
        @URL
        @Pattern(regexp = "^https?://.*", message = "URL must use http or https")
        @Size(max = 2048)
        String url,
    @Pattern(regexp = "^[0-9A-Za-z]{3,16}$", message = "custom code must be 3-16 base62 characters")
        String customCode,
    Instant expiresAt) {

  public static CreateLinkRequest of(String url, String customCode, Instant expiresAt) {
    return new CreateLinkRequest(url, customCode, expiresAt);
  }
}
