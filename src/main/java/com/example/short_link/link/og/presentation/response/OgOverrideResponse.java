package com.example.short_link.link.og.presentation.response;

import com.example.short_link.link.domain.ShortCode;

public record OgOverrideResponse(
    ShortCode shortCode, String ogTitle, String ogDescription, String ogImage) {}
