package com.example.short_link.user.application.read;

public record UserProfile(
    Long id,
    String username,
    String displayName,
    String bio,
    String avatarUrl,
    String bannerUrl,
    String profileTheme,
    String socials,
    boolean hideFollowerCount,
    boolean locked,
    boolean statsPublic,
    String timezone) {}
