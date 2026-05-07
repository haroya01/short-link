package com.example.short_link.link.api;

import java.time.Instant;

public record LinkEventResponse(
    Instant clickedAt,
    String country,
    String region,
    String city,
    String device,
    String os,
    String browser,
    String referrer,
    String referrerHost,
    String channel,
    String language,
    boolean bot,
    String botName,
    String ipMasked) {}
