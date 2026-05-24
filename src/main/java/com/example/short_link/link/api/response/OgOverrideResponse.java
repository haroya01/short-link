package com.example.short_link.link.api.response;

public record OgOverrideResponse(
    String shortCode, String ogTitle, String ogDescription, String ogImage) {}
