package com.example.short_link.link.redirect.application;

public record RedirectVisit(
    String referrer,
    String userAgent,
    String clientIp,
    String acceptLanguage,
    String sourceChannel,
    Long postId,
    boolean gpc,
    String fetchSite,
    boolean prefetch) {}
