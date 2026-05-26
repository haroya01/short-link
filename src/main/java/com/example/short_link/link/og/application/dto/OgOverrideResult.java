package com.example.short_link.link.og.application.dto;

import com.example.short_link.link.domain.ShortCode;

public record OgOverrideResult(
    ShortCode shortCode, String ogTitle, String ogDescription, String ogImage) {}
