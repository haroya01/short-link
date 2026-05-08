package com.example.short_link.link.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LinkPreviewCrawlerDetectorTest {

  private final LinkPreviewCrawlerDetector detector = new LinkPreviewCrawlerDetector();

  @Test
  void recognizesKakaoTalkScrap() {
    assertThat(detector.isCrawler("kakaotalk-scrap/1.0")).isTrue();
  }

  @Test
  void recognizesSlackbot() {
    assertThat(detector.isCrawler("Slackbot-LinkExpanding 1.0 (+https://api.slack.com/robots)"))
        .isTrue();
  }

  @Test
  void recognizesTwitterbot() {
    assertThat(detector.isCrawler("Twitterbot/1.0")).isTrue();
  }

  @Test
  void recognizesFacebookCrawler() {
    assertThat(detector.isCrawler("facebookexternalhit/1.1")).isTrue();
  }

  @Test
  void recognizesDiscord() {
    assertThat(
            detector.isCrawler("Mozilla/5.0 (compatible; Discordbot/2.0; +https://discordapp.com)"))
        .isTrue();
  }

  @Test
  void doesNotMatchRegularBrowser() {
    assertThat(
            detector.isCrawler(
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 Safari/605.1.15"))
        .isFalse();
  }

  @Test
  void doesNotMatchMobileBrowser() {
    assertThat(
            detector.isCrawler(
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148"))
        .isFalse();
  }

  @Test
  void handlesNullAndBlank() {
    assertThat(detector.isCrawler(null)).isFalse();
    assertThat(detector.isCrawler("")).isFalse();
    assertThat(detector.isCrawler("   ")).isFalse();
  }

  @Test
  void caseInsensitive() {
    assertThat(detector.isCrawler("LinkedInBot/1.0")).isTrue();
    assertThat(detector.isCrawler("LINKEDINBOT/1.0")).isTrue();
  }
}
