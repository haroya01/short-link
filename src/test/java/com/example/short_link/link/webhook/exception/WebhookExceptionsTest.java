package com.example.short_link.link.webhook.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WebhookExceptionsTest {

  @Test
  void invalidWebhookUrlMessage() {
    WebhookException ex = new WebhookException(WebhookErrorCode.INVALID_WEBHOOK_URL);
    assertThat(ex).hasMessageContaining("webhook url");
  }

  @Test
  void tooManyWebhooksCarriesLimit() {
    WebhookException ex = new WebhookException(WebhookErrorCode.TOO_MANY_WEBHOOKS, 5);
    assertThat(ex.properties().get("limit")).isEqualTo(5);
    assertThat(ex).hasMessageContaining("5");
  }

  @Test
  void webhookNotFoundCarriesCode() {
    WebhookException ex = new WebhookException(WebhookErrorCode.WEBHOOK_NOT_FOUND);
    assertThat(ex.errorCode()).isEqualTo(WebhookErrorCode.WEBHOOK_NOT_FOUND);
  }
}
