package com.example.short_link.link.webhook.presentation.request;

import com.example.short_link.link.webhook.domain.WebhookDeliveryMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record LinkWebhookConfigRequest(
    Boolean includeBots,
    @Min(1) @Max(100) Integer sampleRate,
    Boolean batchEnabled,
    Integer dailyQuota,
    @Size(max = 255) String referrerHostFilter,
    @Size(max = 100) String utmSourceFilter,
    WebhookDeliveryMode deliveryMode,
    @Min(0) @Max(23) Integer summaryHourOfDay,
    @Min(1) Integer spikeThreshold,
    @Min(1) Integer spikeWindowMinutes) {}
