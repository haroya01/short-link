package com.example.short_link.link.webhook.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class LinkWebhookEntityDeliveryModeTest {

  private LinkWebhookEntity hook() {
    return new LinkWebhookEntity(1L, "https://example.com/hook", "secret", "n");
  }

  @Test
  void defaultsToPerEventMode() {
    assertThat(hook().getDeliveryMode()).isEqualTo(WebhookDeliveryMode.PER_EVENT);
  }

  @Test
  void changeToDailySummaryRequiresHourOfDay() {
    LinkWebhookEntity h = hook();

    assertThatThrownBy(
            () -> h.changeDeliveryMode(WebhookDeliveryMode.DAILY_SUMMARY, null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void changeToDailySummaryRejectsHourBelowZero() {
    LinkWebhookEntity h = hook();

    assertThatThrownBy(
            () -> h.changeDeliveryMode(WebhookDeliveryMode.DAILY_SUMMARY, -1, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void changeToDailySummaryRejectsHourAbove23() {
    LinkWebhookEntity h = hook();

    assertThatThrownBy(
            () -> h.changeDeliveryMode(WebhookDeliveryMode.DAILY_SUMMARY, 24, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void changeToDailySummaryPersistsHourOfDay() {
    LinkWebhookEntity h = hook();

    h.changeDeliveryMode(WebhookDeliveryMode.DAILY_SUMMARY, 9, null, null);

    assertThat(h.getSummaryHourOfDay()).isEqualTo(9);
  }

  @Test
  void changeToThresholdSpikeRequiresThreshold() {
    LinkWebhookEntity h = hook();

    assertThatThrownBy(
            () -> h.changeDeliveryMode(WebhookDeliveryMode.THRESHOLD_SPIKE, null, null, 10))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void changeToThresholdSpikeRequiresWindow() {
    LinkWebhookEntity h = hook();

    assertThatThrownBy(
            () -> h.changeDeliveryMode(WebhookDeliveryMode.THRESHOLD_SPIKE, null, 50, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void changeToThresholdSpikeRejectsZeroThreshold() {
    LinkWebhookEntity h = hook();

    assertThatThrownBy(() -> h.changeDeliveryMode(WebhookDeliveryMode.THRESHOLD_SPIKE, null, 0, 10))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void changeToThresholdSpikePersistsThreshold() {
    LinkWebhookEntity h = hook();

    h.changeDeliveryMode(WebhookDeliveryMode.THRESHOLD_SPIKE, null, 50, 10);

    assertThat(h.getSpikeThreshold()).isEqualTo(50);
  }

  @Test
  void changeToThresholdSpikePersistsWindow() {
    LinkWebhookEntity h = hook();

    h.changeDeliveryMode(WebhookDeliveryMode.THRESHOLD_SPIKE, null, 50, 10);

    assertThat(h.getSpikeWindowMinutes()).isEqualTo(10);
  }

  @Test
  void changeToBothPersistsAllFields() {
    LinkWebhookEntity h = hook();

    h.changeDeliveryMode(WebhookDeliveryMode.BOTH, 9, 50, 10);

    assertThat(h.getDeliveryMode()).isEqualTo(WebhookDeliveryMode.BOTH);
  }

  @Test
  void changeBackToPerEventClearsSummaryHour() {
    LinkWebhookEntity h = hook();
    h.changeDeliveryMode(WebhookDeliveryMode.DAILY_SUMMARY, 9, null, null);

    h.changeDeliveryMode(WebhookDeliveryMode.PER_EVENT, null, null, null);

    assertThat(h.getSummaryHourOfDay()).isNull();
  }

  @Test
  void changeBackToPerEventClearsSpikeThreshold() {
    LinkWebhookEntity h = hook();
    h.changeDeliveryMode(WebhookDeliveryMode.BOTH, 9, 50, 10);

    h.changeDeliveryMode(WebhookDeliveryMode.PER_EVENT, null, null, null);

    assertThat(h.getSpikeThreshold()).isNull();
  }

  @Test
  void nullModeIsNoOp() {
    LinkWebhookEntity h = hook();

    h.changeDeliveryMode(null, 9, 50, 10);

    assertThat(h.getDeliveryMode()).isEqualTo(WebhookDeliveryMode.PER_EVENT);
  }

  @Test
  void markSummarySentStoresDate() {
    LinkWebhookEntity h = hook();
    LocalDate d = LocalDate.of(2026, 5, 26);

    h.markSummarySent(d);

    assertThat(h.getSummaryLastSentDate()).isEqualTo(d);
  }

  @Test
  void markSpikeFiredStoresInstant() {
    LinkWebhookEntity h = hook();
    Instant t = Instant.parse("2026-05-26T12:00:00Z");

    h.markSpikeFired(t);

    assertThat(h.getSpikeLastFiredAt()).isEqualTo(t);
  }

  @Test
  void sendsDailySummaryTrueForDailyMode() {
    assertThat(WebhookDeliveryMode.DAILY_SUMMARY.sendsDailySummary()).isTrue();
  }

  @Test
  void sendsDailySummaryTrueForBothMode() {
    assertThat(WebhookDeliveryMode.BOTH.sendsDailySummary()).isTrue();
  }

  @Test
  void sendsDailySummaryFalseForPerEvent() {
    assertThat(WebhookDeliveryMode.PER_EVENT.sendsDailySummary()).isFalse();
  }

  @Test
  void sendsSpikeAlertTrueForSpikeMode() {
    assertThat(WebhookDeliveryMode.THRESHOLD_SPIKE.sendsSpikeAlert()).isTrue();
  }

  @Test
  void sendsSpikeAlertTrueForBothMode() {
    assertThat(WebhookDeliveryMode.BOTH.sendsSpikeAlert()).isTrue();
  }

  @Test
  void sendsPerEventTrueOnlyForPerEvent() {
    assertThat(WebhookDeliveryMode.PER_EVENT.sendsPerEvent()).isTrue();
  }

  @Test
  void sendsPerEventFalseForDaily() {
    assertThat(WebhookDeliveryMode.DAILY_SUMMARY.sendsPerEvent()).isFalse();
  }
}
