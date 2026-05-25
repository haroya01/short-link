package com.example.short_link.link.application.dto;

public record LinkProtectionResult(
    String shortCode, boolean passwordProtected, Integer maxViews, int viewCount) {}
