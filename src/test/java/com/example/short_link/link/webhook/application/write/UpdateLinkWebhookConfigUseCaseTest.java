package com.example.short_link.link.webhook.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.short_link.link.webhook.domain.LinkWebhookEntity;
import com.example.short_link.link.webhook.domain.WebhookDeliveryMode;
import org.junit.jupiter.api.Test;

class UpdateLinkWebhookConfigUseCaseTest {

  private final WebhookOwnership ownership = mock(WebhookOwnership.class);
  private final UpdateLinkWebhookConfigUseCase useCase =
      new UpdateLinkWebhookConfigUseCase(ownership);

  private LinkWebhookEntity stubHook() {
    LinkWebhookEntity hook = new LinkWebhookEntity(1L, "https://example.com/h", "secret", "n");
    when(ownership.ownedHook(7L, "abcde", 99L)).thenReturn(hook);
    return hook;
  }

  @Test
  void persistsSampleRate() {
    LinkWebhookEntity hook = stubHook();

    var summary =
        useCase.execute(
            new UpdateLinkWebhookConfigCommand(
                7L, "abcde", 99L, null, 30, null, null, null, null, null, null, null, null));

    assertThat(summary.sampleRate()).isEqualTo(30);
  }

  @Test
  void switchToDailySummaryPersistsHour() {
    stubHook();

    var summary =
        useCase.execute(
            new UpdateLinkWebhookConfigCommand(
                7L,
                "abcde",
                99L,
                null,
                null,
                null,
                null,
                null,
                null,
                WebhookDeliveryMode.DAILY_SUMMARY,
                9,
                null,
                null));

    assertThat(summary.summaryHourOfDay()).isEqualTo(9);
  }

  @Test
  void switchToThresholdSpikePersistsThreshold() {
    stubHook();

    var summary =
        useCase.execute(
            new UpdateLinkWebhookConfigCommand(
                7L,
                "abcde",
                99L,
                null,
                null,
                null,
                null,
                null,
                null,
                WebhookDeliveryMode.THRESHOLD_SPIKE,
                null,
                50,
                10));

    assertThat(summary.spikeThreshold()).isEqualTo(50);
  }

  @Test
  void switchToBothPersistsMode() {
    stubHook();

    var summary =
        useCase.execute(
            new UpdateLinkWebhookConfigCommand(
                7L,
                "abcde",
                99L,
                null,
                null,
                null,
                null,
                null,
                null,
                WebhookDeliveryMode.BOTH,
                21,
                100,
                5));

    assertThat(summary.deliveryMode()).isEqualTo(WebhookDeliveryMode.BOTH);
  }

  @Test
  void omittedDeliveryModeLeavesItUnchanged() {
    LinkWebhookEntity hook = stubHook();

    var summary =
        useCase.execute(
            new UpdateLinkWebhookConfigCommand(
                7L, "abcde", 99L, true, null, null, null, null, null, null, null, null, null));

    assertThat(summary.deliveryMode()).isEqualTo(WebhookDeliveryMode.PER_EVENT);
  }
}
