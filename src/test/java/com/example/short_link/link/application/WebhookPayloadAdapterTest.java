package com.example.short_link.link.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.application.helper.WebhookFormat;
import com.example.short_link.link.application.helper.WebhookPayloadAdapter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WebhookPayloadAdapterTest {

  private static final Map<String, Object> SAMPLE_CLICK =
      Map.of(
          "type", "click",
          "linkId", 42L,
          "occurredAt", "2026-01-02T03:04:05Z",
          "countryCode", "KR",
          "deviceClass", "mobile",
          "channel", "twitter.com",
          "utmSource", "newsletter",
          "bot", false);

  @Test
  void genericReturnsClickUnchanged() {
    Map<String, Object> body =
        WebhookPayloadAdapter.buildClick(WebhookFormat.GENERIC, SAMPLE_CLICK);
    assertThat(body).isSameAs(SAMPLE_CLICK);
  }

  @Test
  void discordBodyHasEmbedsAndBrandColor() {
    Map<String, Object> body =
        WebhookPayloadAdapter.buildClick(WebhookFormat.DISCORD, SAMPLE_CLICK);
    assertThat(body).containsKey("embeds");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> embeds = (List<Map<String, Object>>) body.get("embeds");
    assertThat(embeds).hasSize(1);
    Map<String, Object> embed = embeds.get(0);
    assertThat(embed.get("title").toString()).contains("link #42");
    assertThat(embed.get("color")).isEqualTo(0x059669);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> fields = (List<Map<String, Object>>) embed.get("fields");
    assertThat(fields).extracting(f -> f.get("name")).contains("Country", "Device", "Channel");
  }

  @Test
  void slackBodyHasTextAndBlocks() {
    Map<String, Object> body = WebhookPayloadAdapter.buildClick(WebhookFormat.SLACK, SAMPLE_CLICK);
    assertThat(body).containsKeys("text", "blocks");
    assertThat(body.get("text").toString()).contains("link #42");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> blocks = (List<Map<String, Object>>) body.get("blocks");
    assertThat(blocks).isNotEmpty();
    assertThat(blocks.get(0).get("type")).isEqualTo("header");
  }

  @Test
  void discordBatchUsesEmbeds() {
    Map<String, Object> body =
        WebhookPayloadAdapter.buildBatch(WebhookFormat.DISCORD, 42L, List.of(SAMPLE_CLICK));
    assertThat(body).containsKey("embeds");
    assertThat(body.get("content").toString()).contains("batch");
  }

  @Test
  void slackBatchUsesText() {
    Map<String, Object> body =
        WebhookPayloadAdapter.buildBatch(WebhookFormat.SLACK, 42L, List.of(SAMPLE_CLICK));
    assertThat(body.get("text").toString()).contains("link #42");
  }

  @Test
  void genericBatchPreservesEvents() {
    Map<String, Object> body =
        WebhookPayloadAdapter.buildBatch(WebhookFormat.GENERIC, 42L, List.of(SAMPLE_CLICK));
    assertThat(body).containsEntry("type", "click.batch").containsEntry("count", 1);
    assertThat(body.get("events")).isEqualTo(List.of(SAMPLE_CLICK));
  }

  @Test
  void skipsBlankFields() {
    Map<String, Object> sparse =
        Map.of(
            "linkId", 1L,
            "occurredAt", "2026-01-01T00:00:00Z",
            "countryCode", "",
            "deviceClass", "",
            "channel", "",
            "utmSource", "",
            "bot", false);
    Map<String, Object> body = WebhookPayloadAdapter.buildClick(WebhookFormat.DISCORD, sparse);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> embeds = (List<Map<String, Object>>) body.get("embeds");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> fields = (List<Map<String, Object>>) embeds.get(0).get("fields");
    assertThat(fields).isEmpty();
  }
}
