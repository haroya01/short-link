package com.example.short_link.link.application;

import java.time.Instant;

public record LinkEventView(
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
