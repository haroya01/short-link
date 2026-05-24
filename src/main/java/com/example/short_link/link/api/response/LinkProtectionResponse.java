package com.example.short_link.link.api.response;

public record LinkProtectionResponse(
    String shortCode, boolean passwordProtected, Integer maxViews, int viewCount) {}
