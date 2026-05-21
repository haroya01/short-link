package com.example.short_link.campaign.domain;

/**
 * Campaign 이 ENDED 로 전환될 때 각 batch 대표 link 에 어떤 만료 정책을 박을지 결정한다.
 *
 * <ul>
 *   <li>KEEP — link 그대로 둔다 (인쇄된 QR 이 종료 후에도 원래 목적지로 이동).
 *   <li>EXPIRE — link.expiresAt = endedAt 으로 박는다. 만료 페이지 표시.
 *   <li>REDIRECT — link.expiresAt = endedAt + link.expiredRedirectUrl = postEndDestinationUrl 로
 *       박는다. 만료 시 다른 페이지로 이동.
 * </ul>
 *
 * <p>적용 시점은 Campaign ENDED 전환 시 단 한 번. 이후 정책 변경은 명시적 "재적용" 액션 필요 — 자동 동기화하지 않는다 (인쇄된 QR 의 동작이 사용자
 * 모르는 사이 바뀌면 안 됨).
 */
public enum CampaignPostEndAction {
  KEEP,
  EXPIRE,
  REDIRECT
}
