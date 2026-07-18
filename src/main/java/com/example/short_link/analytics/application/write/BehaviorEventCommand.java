package com.example.short_link.analytics.application.write;

/**
 * 비콘 배치의 이벤트 한 건. 검증 전 원자료 — 이름/대상/깊이의 화이트리스트 판정은 {@link RecordBehaviorEventsUseCase} 가 한다(비콘 계약상
 * 불합격 건은 에러가 아니라 조용한 드랍).
 */
public record BehaviorEventCommand(
    String name, Long postId, String targetType, String targetId, Integer depthPct, Long dwellMs) {}
