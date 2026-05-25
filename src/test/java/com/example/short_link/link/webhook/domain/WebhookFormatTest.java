package com.example.short_link.link.webhook.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WebhookFormatTest {

  @Test
  void detectsDiscord() {
    assertThat(WebhookFormat.detect("https://discord.com/api/webhooks/123/abc"))
        .isEqualTo(WebhookFormat.DISCORD);
    assertThat(WebhookFormat.detect("https://discordapp.com/api/webhooks/123/abc"))
        .isEqualTo(WebhookFormat.DISCORD);
    assertThat(WebhookFormat.detect("https://canary.discord.com/api/webhooks/123/abc"))
        .isEqualTo(WebhookFormat.DISCORD);
  }

  @Test
  void detectsSlack() {
    assertThat(WebhookFormat.detect("https://hooks.slack.com/services/T0/B0/secret"))
        .isEqualTo(WebhookFormat.SLACK);
  }

  @Test
  void fallsBackToGeneric() {
    assertThat(WebhookFormat.detect("https://example.com/hook")).isEqualTo(WebhookFormat.GENERIC);
    assertThat(WebhookFormat.detect("https://my.notdiscord.example/api"))
        .isEqualTo(WebhookFormat.GENERIC);
    assertThat(WebhookFormat.detect(null)).isEqualTo(WebhookFormat.GENERIC);
    assertThat(WebhookFormat.detect("not-a-url")).isEqualTo(WebhookFormat.GENERIC);
  }
}
