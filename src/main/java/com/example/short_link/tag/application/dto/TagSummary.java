package com.example.short_link.tag.application.dto;

import java.time.Instant;

public record TagSummary(Long id, String name, String color, long linkCount, Instant createdAt) {}
