package com.example.short_link.link.og.presentation.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OgOverrideRequest(
    @Size(max = 300) String ogTitle,
    @Size(max = 800) String ogDescription,
    @Pattern(regexp = "^(https?://.*)?$", message = "ogImage must use http or https")
        @Size(max = 1024)
        String ogImage) {}
