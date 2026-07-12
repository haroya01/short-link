package com.example.short_link.abuse.domain;

/**
 * 신고 사유 정형 코드 — iOS/웹의 6종 선택지와 1:1. 관리자가 큐에서 사유를 정렬·집계할 수 있게 자유서술(detail)과 분리한다. 코드가 없는 legacy
 * 신고(출시 전 free-text 만 있던 행)는 null 로 남고 뷰가 그대로 노출한다.
 */
public enum AbuseReason {
  SPAM,
  HARASSMENT,
  VIOLENCE,
  SEXUAL,
  COPYRIGHT,
  OTHER
}
