package com.example.short_link.link.application.dto;

import com.example.short_link.link.domain.LinkId;
import java.time.Instant;

/**
 * Fired right after a click is persisted. Carries only the lightweight slice the live-stream
 * handler needs — no IP, no UA — so it can be safely fanned out to multiple subscribers without
 * leaking PII. {@code channel} is the referrer host (kept under that name for backwards-compat with
 * existing SSE/webhook consumers).
 *
 * <p>{@code shortCode}/{@code ownerUserId} 는 계정 단위 라이브 스트림(대시보드 "첫 클릭 도착" 모먼트)의 팬아웃 키 — 익명 링크는
 * ownerUserId 가 null 이고 계정 채널로는 나가지 않는다.
 */
public record ClickRecordedEvent(
    LinkId linkId,
    String shortCode,
    Long ownerUserId,
    Instant occurredAt,
    String countryCode,
    String deviceClass,
    String channel,
    boolean bot,
    String utmSource) {}
