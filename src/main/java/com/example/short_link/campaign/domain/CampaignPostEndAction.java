package com.example.short_link.campaign.domain;

/** 종료 시 대표 링크에 적용한다. 인쇄된 QR 동작이 예고 없이 바뀌지 않도록 이후 정책 변경은 명시적 재적용이 필요하다. */
public enum CampaignPostEndAction {
  KEEP,
  EXPIRE,
  REDIRECT
}
