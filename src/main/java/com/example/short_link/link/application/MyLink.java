package com.example.short_link.link.application;

import java.time.Instant;

public record MyLink(String shortCode, String originalUrl, Instant createdAt, Instant expiresAt) {}
