package com.example.short_link.link.redirect.application;

/** Visitor metadata decoded by the HTTP entry point before the redirect decision pipeline. */
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
