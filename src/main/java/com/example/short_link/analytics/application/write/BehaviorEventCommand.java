package com.example.short_link.analytics.application.write;

/** 검증 전 입력이며, 유효하지 않은 이벤트는 오류 응답 없이 제외한다. */
public record BehaviorEventCommand(
    String name, Long postId, String targetType, String targetId, Integer depthPct, Long dwellMs) {}
