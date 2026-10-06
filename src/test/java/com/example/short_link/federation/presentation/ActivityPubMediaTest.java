package com.example.short_link.federation.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ActivityPubMediaTest {

  @Test
  void onlyAnExplicitHtmlRequestWithoutJsonGetsTheProfile() {
    assertThat(ActivityPubMedia.wantsHtml("text/html,application/xhtml+xml,*/*;q=0.8")).isTrue();
    assertThat(ActivityPubMedia.wantsHtml("application/activity+json")).isFalse();
    assertThat(ActivityPubMedia.wantsHtml("text/html, application/ld+json")).isFalse();
    assertThat(ActivityPubMedia.wantsHtml("*/*")).isFalse();
    assertThat(ActivityPubMedia.wantsHtml("")).isFalse();
    assertThat(ActivityPubMedia.wantsHtml(null)).isFalse();
    assertThat(ActivityPubMedia.wantsHtml(";;;")).isFalse();
  }

  @Test
  void summaryEscapesHtmlAndKeepsLineBreaks() {
    assertThat(ActorController.summary(" a\n<b> ")).isEqualTo("<p>a<br>&lt;b&gt;</p>");
    assertThat(ActorController.summary(" ")).isNull();
    assertThat(ActorController.summary(null)).isNull();
  }
}
