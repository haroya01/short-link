package com.example.short_link.link.application;

public record LinkProtectionResult(
    String shortCode, boolean passwordProtected, Integer maxViews, int viewCount) {}
