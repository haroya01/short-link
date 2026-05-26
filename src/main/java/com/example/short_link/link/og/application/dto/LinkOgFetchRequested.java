package com.example.short_link.link.og.application.dto;

import com.example.short_link.link.domain.ShortCode;

public record LinkOgFetchRequested(ShortCode shortCode, String originalUrl) {}
