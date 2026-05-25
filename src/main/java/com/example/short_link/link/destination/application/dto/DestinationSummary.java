package com.example.short_link.link.destination.application.dto;

import java.time.Instant;

public record DestinationSummary(
    Long id,
    String url,
    int weight,
    String label,
    boolean enabled,
    String countryCode,
    String deviceClass,
    String os,
    Instant createdAt) {}
