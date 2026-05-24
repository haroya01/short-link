package com.example.short_link.link.api.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LinkDestinationAddRequest(
    @NotBlank @Size(max = 2048) String url,
    @Min(1) @Max(100) Integer weight,
    @Size(max = 40) String label,
    @Pattern(regexp = "^[A-Za-z]{2}$", message = "countryCode must be ISO-3166 alpha-2")
        String countryCode,
    @Pattern(
            regexp = "^(mobile|tablet|desktop)?$",
            message = "deviceClass must be mobile/tablet/desktop")
        String deviceClass,
    @Pattern(
            regexp = "^(ios|android|windows|macos|linux)?$",
            message = "os must be ios/android/windows/macos/linux")
        String os) {}
