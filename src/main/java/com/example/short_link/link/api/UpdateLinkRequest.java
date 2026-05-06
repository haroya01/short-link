package com.example.short_link.link.api;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.hibernate.validator.constraints.URL;

public record UpdateLinkRequest(
    @URL @Pattern(regexp = "^https?://.*", message = "URL must use http or https") @Size(max = 2048)
        String originalUrl,
    Instant expiresAt) {}
