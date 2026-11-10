package com.example.short_link.link.application.dto;

import com.example.short_link.link.domain.LinkId;
import java.time.Instant;

public record RecordedClick(
    Long clickId,
    LinkId linkId,
    Instant occurredAt,
    String countryCode,
    String deviceClass,
    String referrerHost,
    boolean bot,
    String utmSource) {}
