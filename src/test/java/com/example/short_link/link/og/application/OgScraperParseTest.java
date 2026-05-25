package com.example.short_link.link.og.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.application.dto.OgMetadata;
import org.junit.jupiter.api.Test;

class OgScraperParseTest {

  @Test
  void picksOgTitleOverHtmlTitle() {
    String html =
        """
        <html><head>
          <title>HTML title</title>
          <meta property="og:title" content="OG title">
        </head><body></body></html>
        """;
    OgMetadata m = OgScraper.parseHtml(html, "https://example.com/");
    assertThat(m.title()).isEqualTo("OG title");
  }

  @Test
  void fallsBackToHtmlTitleWhenOgMissing() {
    String html =
        """
        <html><head>
          <title>Just the title</title>
        </head><body></body></html>
        """;
    OgMetadata m = OgScraper.parseHtml(html, "https://example.com/");
    assertThat(m.title()).isEqualTo("Just the title");
  }

  @Test
  void picksOgDescriptionOverMetaDescription() {
    String html =
        """
        <html><head>
          <meta name="description" content="meta desc">
          <meta property="og:description" content="og desc">
        </head><body></body></html>
        """;
    OgMetadata m = OgScraper.parseHtml(html, "https://example.com/");
    assertThat(m.description()).isEqualTo("og desc");
  }

  @Test
  void fallsBackToMetaDescription() {
    String html =
        """
        <html><head>
          <meta name="description" content="meta desc">
        </head><body></body></html>
        """;
    OgMetadata m = OgScraper.parseHtml(html, "https://example.com/");
    assertThat(m.description()).isEqualTo("meta desc");
  }

  @Test
  void resolvesRelativeOgImageAgainstBase() {
    String html =
        """
        <html><head>
          <meta property="og:image" content="/static/cover.png">
        </head><body></body></html>
        """;
    OgMetadata m = OgScraper.parseHtml(html, "https://example.com/article/1");
    assertThat(m.image()).isEqualTo("https://example.com/static/cover.png");
  }

  @Test
  void rejectsNonHttpImageScheme() {
    String html =
        """
        <html><head>
          <meta property="og:image" content="javascript:alert(1)">
        </head><body></body></html>
        """;
    OgMetadata m = OgScraper.parseHtml(html, "https://example.com/");
    assertThat(m.image()).isNull();
  }

  @Test
  void truncatesLongStrings() {
    String longTitle = "x".repeat(500);
    String html = "<html><head><title>" + longTitle + "</title></head></html>";
    OgMetadata m = OgScraper.parseHtml(html, "https://example.com/");
    assertThat(m.title()).hasSize(300);
  }

  @Test
  void emptyDocumentYieldsEmptyMetadata() {
    OgMetadata m = OgScraper.parseHtml("<html></html>", "https://example.com/");
    assertThat(m.hasAny()).isFalse();
  }
}
