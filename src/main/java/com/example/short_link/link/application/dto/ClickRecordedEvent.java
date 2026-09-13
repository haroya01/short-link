package com.example.short_link.link.application.dto;

import com.example.short_link.link.domain.LinkId;
import java.time.Instant;

/**
 * Published after persistence; excludes IP and UA to keep subscriber fan-out free of PII. {@code
 * referrerHost} is serialized as {@code channel} at SSE/webhook boundaries for compatibility. A
 * null {@code ownerUserId} denotes an anonymous link and excludes it from account streams.
 */
public record ClickRecordedEvent(
    LinkId linkId,
    String shortCode,
    Long ownerUserId,
    Instant occurredAt,
    String countryCode,
    String deviceClass,
    String referrerHost,
    boolean bot,
    String utmSource) {}
