package com.example.short_link.link.webhook.application.write;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.link.domain.ShortCode;
import org.junit.jupiter.api.Test;

class UpdateLinkWebhookConfigCommandTest {

  @Test
  void rejectsNegativeSummaryHour() {
    assertThatThrownBy(
            () ->
                new UpdateLinkWebhookConfigCommand(
                    1L,
                    new ShortCode("abcde"),
                    1L,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    -1,
                    null,
                    null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsSummaryHourAt24() {
    assertThatThrownBy(
            () ->
                new UpdateLinkWebhookConfigCommand(
                    1L,
                    new ShortCode("abcde"),
                    1L,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    24,
                    null,
                    null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsSpikeThresholdZero() {
    assertThatThrownBy(
            () ->
                new UpdateLinkWebhookConfigCommand(
                    1L,
                    new ShortCode("abcde"),
                    1L,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    0,
                    null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsSpikeWindowZero() {
    assertThatThrownBy(
            () ->
                new UpdateLinkWebhookConfigCommand(
                    1L,
                    new ShortCode("abcde"),
                    1L,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    0))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
