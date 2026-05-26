package com.example.short_link.link.webhook.application.helper;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.domain.ShortCode;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DailySummaryPayloadTest {

  private DailySummaryPayload sample() {
    return new DailySummaryPayload(
        new ShortCode("abc12345"),
        "2026-05-25T00:00",
        "2026-05-25T23:59:59",
        142L,
        130L,
        12L,
        88L,
        "twitter",
        "KR",
        "Mobile",
        21,
        24L,
        0.08,
        -0.05);
  }

  @Test
  void typeIsDailySummary() {
    assertThat(sample().toJsonMap()).containsEntry("type", "daily_summary");
  }

  @Test
  void shortCodeIsPropagated() {
    assertThat(sample().toJsonMap()).containsEntry("shortCode", "abc12345");
  }

  @Test
  void clicksBlockCarriesTotal() {
    @SuppressWarnings("unchecked")
    Map<String, Object> clicks = (Map<String, Object>) sample().toJsonMap().get("clicks");
    assertThat(clicks).containsEntry("total", 142L);
  }

  @Test
  void clicksBlockCarriesUnique() {
    @SuppressWarnings("unchecked")
    Map<String, Object> clicks = (Map<String, Object>) sample().toJsonMap().get("clicks");
    assertThat(clicks).containsEntry("unique", 88L);
  }

  @Test
  void topBlockCarriesChannel() {
    @SuppressWarnings("unchecked")
    Map<String, Object> top = (Map<String, Object>) sample().toJsonMap().get("top");
    assertThat(top).containsEntry("channel", "twitter");
  }

  @Test
  void peakBlockCarriesHour() {
    @SuppressWarnings("unchecked")
    Map<String, Object> peak = (Map<String, Object>) sample().toJsonMap().get("peak");
    assertThat(peak).containsEntry("hour", 21);
  }

  @Test
  void deltaCarriesYesterdayRatio() {
    @SuppressWarnings("unchecked")
    Map<String, Object> delta = (Map<String, Object>) sample().toJsonMap().get("delta");
    assertThat(delta).containsEntry("vsYesterday", 0.08);
  }

  @Test
  void deltaAllowsNullVs7DayAvg() {
    DailySummaryPayload p =
        new DailySummaryPayload(
            new ShortCode("xxx"), "a", "b", 1L, 1L, 0L, 1L, null, null, null, 0, 0L, null, null);
    @SuppressWarnings("unchecked")
    Map<String, Object> delta = (Map<String, Object>) p.toJsonMap().get("delta");
    assertThat(delta).containsEntry("vs7DayAvg", null);
  }
}
