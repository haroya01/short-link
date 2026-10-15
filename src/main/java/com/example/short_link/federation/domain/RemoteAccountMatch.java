package com.example.short_link.federation.domain;

public record RemoteAccountMatch(
    Long id,
    String username,
    String domain,
    String displayName,
    String avatarUrl,
    String url,
    boolean following,
    boolean requested) {}
