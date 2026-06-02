package com.example.short_link.link.og.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.short_link.link.application.dto.OgMetadata;
import com.example.short_link.link.og.application.dto.LinkPreview;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LinkPreviewServiceTest {

  @Mock private OgScraper ogScraper;
  @InjectMocks private LinkPreviewService service;

  @Test
  void httpUrlMapsOgMetadata() {
    when(ogScraper.fetch("https://example.com"))
        .thenReturn(new OgMetadata("Title", "Desc", "https://img.png"));

    LinkPreview p = service.fetch("https://example.com");

    assertThat(p.url()).isEqualTo("https://example.com");
    assertThat(p.title()).isEqualTo("Title");
    assertThat(p.description()).isEqualTo("Desc");
    assertThat(p.image()).isEqualTo("https://img.png");
  }

  @Test
  void nonHttpUrlReturnsBareWithoutScraping() {
    LinkPreview p = service.fetch("ftp://files.example.com/x");

    assertThat(p.url()).isEqualTo("ftp://files.example.com/x");
    assertThat(p.title()).isNull();
    assertThat(p.image()).isNull();
  }

  @Test
  void scrapeFailureReturnsBare() {
    when(ogScraper.fetch("https://boom.example")).thenThrow(new RuntimeException("network"));

    LinkPreview p = service.fetch("https://boom.example");

    assertThat(p.url()).isEqualTo("https://boom.example");
    assertThat(p.title()).isNull();
  }
}
