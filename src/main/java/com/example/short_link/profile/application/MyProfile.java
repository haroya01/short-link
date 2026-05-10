package com.example.short_link.profile.application;

public record MyProfile(
    String username,
    String bio,
    String theme,
    String publicUrl,
    String avatarUrl,
    String bannerUrl) {}
