package com.example.short_link.link.application.dto;

import com.example.short_link.link.domain.ShortCode;

public record LinkUrlRef(Long userId, ShortCode shortCode, String originalUrl) {}
