package com.example.short_link.link.api.request;

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
    @Size(max = 500) String expiredMessage) {}
