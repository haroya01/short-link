package com.example.short_link.cta.domain;

/**
 * CTA 의도 분류. P-1b (전환 중심 솔로 비즈니스) 의 핵심 행동 패턴. v0 X 인 explicit goal aggregation 도입 시 이 enum 기반으로
 * 자연스럽게 확장.
 */
public enum CtaPurpose {
  BOOKING,
  SUBSCRIBE,
  PURCHASE,
  CONTACT,
  DOWNLOAD,
  CUSTOM
}
