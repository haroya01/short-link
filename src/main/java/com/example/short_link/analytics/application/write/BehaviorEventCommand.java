package com.example.short_link.analytics.application.write;

public record BehaviorEventCommand(
    String name, Long postId, String targetType, String targetId, Integer depthPct, Long dwellMs) {}
