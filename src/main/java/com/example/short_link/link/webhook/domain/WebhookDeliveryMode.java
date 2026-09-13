package com.example.short_link.link.webhook.domain;

import java.util.Arrays;
import java.util.List;

/**
 * 이 모드는 요약·급증 알림의 추가 구독을 결정한다. 단건 클릭 발송은 모든 모드에서 공통으로 수행한다. 일일 요약은 어제 통계이며, 급증 알림은 설정한 창 동안 재발송하지
 * 않는다.
 */
public enum WebhookDeliveryMode {
  PER_EVENT,
  DAILY_SUMMARY,
  THRESHOLD_SPIKE,
  BOTH;

  public boolean sendsDailySummary() {
    return this == DAILY_SUMMARY || this == BOTH;
  }

  public boolean sendsSpikeAlert() {
    return this == THRESHOLD_SPIKE || this == BOTH;
  }

  public static List<WebhookDeliveryMode> dailySummaryModes() {
    return Arrays.stream(values()).filter(WebhookDeliveryMode::sendsDailySummary).toList();
  }
}
