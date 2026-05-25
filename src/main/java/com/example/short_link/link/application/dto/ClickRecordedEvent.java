package com.example.short_link.link.application.dto;

import java.time.Instant;

/**
 * Fired right after a click is persisted. Carries only the lightweight slice the live-stream
 * handler needs — no IP, no UA — so it can be safely fanned out to multiple subscribers without
 * leaking PII. {@code channel} is the referrer host (kept under that name for backwards-compat with
 * existing SSE/webhook consumers).
 */
public record ClickRecordedEvent(
    Long linkId,
    Instant occurredAt,
    String countryCode,
    String deviceClass,
    String channel,
    boolean bot,
    String utmSource) {}
