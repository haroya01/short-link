package com.example.short_link.link.redirect.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LinkPreviewCrawlerDetectorTest {

  private final LinkPreviewCrawlerDetector detector = new LinkPreviewCrawlerDetector();

  @Test
  void recognizesKakaoTalkScrap() {
    assertThat(detector.crawlerName("kakaotalk-scrap/1.0")).isEqualTo("kakaotalk-scrap");
  }

  @Test
  void recognizesSlackbot() {
    assertThat(detector.crawlerName("Slackbot-LinkExpanding 1.0 (+https://api.slack.com/robots)"))
        .isEqualTo("slackbot-linkexpanding");
  }

  @Test
  void recognizesTwitterbot() {
    assertThat(detector.crawlerName("Twitterbot/1.0")).isEqualTo("twitterbot");
  }

  @Test
  void recognizesFacebookCrawler() {
    assertThat(detector.crawlerName("facebookexternalhit/1.1")).isEqualTo("facebookexternalhit");
  }

  @Test
  void recognizesDiscord() {
    assertThat(
            detector.crawlerName(
                "Mozilla/5.0 (compatible; Discordbot/2.0; +https://discordapp.com)"))
        .isEqualTo("discordbot");
  }

  @Test
  void doesNotMatchRegularBrowser() {
    assertThat(
            detector.crawlerName(
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 Safari/605.1.15"))
        .isNull();
  }

  @Test
  void doesNotMatchMobileBrowser() {
    assertThat(
            detector.crawlerName(
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148"))
        .isNull();
  }

  @Test
  void handlesNullAndBlank() {
    assertThat(detector.crawlerName(null)).isNull();
    assertThat(detector.crawlerName("")).isNull();
    assertThat(detector.crawlerName("   ")).isNull();
  }

  @Test
  void caseInsensitive() {
    assertThat(detector.crawlerName("LinkedInBot/1.0")).isEqualTo("linkedinbot");
    assertThat(detector.crawlerName("LINKEDINBOT/1.0")).isEqualTo("linkedinbot");
  }
}
