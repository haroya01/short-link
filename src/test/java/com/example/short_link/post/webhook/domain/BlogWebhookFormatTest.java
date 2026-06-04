package com.example.short_link.post.webhook.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BlogWebhookFormatTest {

  @Test
  void detectsDiscord() {
    assertThat(BlogWebhookFormat.detect("https://discord.com/api/webhooks/1/abc"))
        .isEqualTo(BlogWebhookFormat.DISCORD);
    assertThat(BlogWebhookFormat.detect("https://ptb.discord.com/api/webhooks/1/abc"))
        .isEqualTo(BlogWebhookFormat.DISCORD);
    assertThat(BlogWebhookFormat.detect("https://discordapp.com/api/webhooks/1/abc"))
        .isEqualTo(BlogWebhookFormat.DISCORD);
  }

  @Test
  void detectsSlack() {
    assertThat(BlogWebhookFormat.detect("https://hooks.slack.com/services/T/B/X"))
        .isEqualTo(BlogWebhookFormat.SLACK);
  }

  @Test
  void defaultsToGenericForOtherHosts() {
    assertThat(BlogWebhookFormat.detect("https://example.com/hook"))
        .isEqualTo(BlogWebhookFormat.GENERIC);
  }

  @Test
  void defaultsToGenericForUnparseableUrl() {
    assertThat(BlogWebhookFormat.detect("not a url")).isEqualTo(BlogWebhookFormat.GENERIC);
    assertThat(BlogWebhookFormat.detect("mailto:x@y.com")).isEqualTo(BlogWebhookFormat.GENERIC);
  }
}
