package com.example.short_link.campaign.domain;

/** DRAFT의 링크도 QR 테스트를 위해 활성화되지만 시작 전 클릭은 기본 통계에서 제외한다. ARCHIVED는 링크에 적용된 종료 정책을 유지한다. */
public enum CampaignStatus {
  DRAFT,
  ACTIVE,
  ENDED,
  ARCHIVED
}
