package com.example.short_link.campaign.domain;

/**
 * Campaign 생명주기. 사용자에게는 UI 에서 직접 노출되지 않고 startsAt/endsAt 기준으로 자동 전환되지만, 도메인 모델 내부에서는 의미 분기가 명확해야 해
 * 4-state 로 유지한다.
 *
 * <ul>
 *   <li>DRAFT — startsAt 전. batch/link 준비 가능. 링크는 활성 (인쇄 발주 전 QR 테스트 가능), 단 stats 기본 집계에선 startsAt
 *       이전 클릭 제외.
 *   <li>ACTIVE — startsAt 이후, endsAt 이전. 정상 운영.
 *   <li>ENDED — endsAt 도달 또는 수동 종료. 이 시점에 postEndAction 이 batch link 에 일괄 박힘.
 *   <li>ARCHIVED — 목록에서 숨김/보관. 링크 동작은 ENDED 시 박힌 값에 따름.
 * </ul>
 */
public enum CampaignStatus {
  DRAFT,
  ACTIVE,
  ENDED,
  ARCHIVED
}
