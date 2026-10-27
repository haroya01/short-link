package com.example.short_link.user.application.read;

public record UserSummary(
    Long id, String username, String displayName, String avatarUrl, String bio, String locale) {}
