package com.example.short_link.link.presentation.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record LinkWebhookConfigRequest(
    Boolean includeBots,
    @Min(1) @Max(100) Integer sampleRate,
    Boolean batchEnabled,
    Integer dailyQuota,
    @Size(max = 255) String referrerHostFilter,
    @Size(max = 100) String utmSourceFilter) {}
