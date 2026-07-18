package com.example.short_link.analytics.presentation.request;

import java.util.List;

/**
 * 행동 비콘 배치의 와이어 포맷. Content-Type 없는 keepalive fetch/sendBeacon 이 보내는 JSON 문자열을 컨트롤러가 로컬 매퍼로 파싱해 이
 * 모양으로 받는다. 검증(이름/대상/깊이 화이트리스트)은 유스케이스 몫 — 여기는 순수 형태만.
 */
public record BehaviorEventsRequest(String sessionId, List<Item> events) {

  public record Item(
      String name,
      Long postId,
      String targetType,
      String targetId,
      Integer depthPct,
      Long dwellMs) {}
}
