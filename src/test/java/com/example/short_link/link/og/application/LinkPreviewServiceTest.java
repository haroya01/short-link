package com.example.short_link.link.og.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.link.application.dto.OgMetadata;
import com.example.short_link.link.og.application.dto.LinkPreview;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

@ExtendWith(MockitoExtension.class)
class LinkPreviewServiceTest {

  @Mock private OgScraper ogScraper;
  @Mock private CacheManager caches;
  @Mock private Cache cache;
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
    LinkPreview file = service.fetch("file:///etc/passwd");
    LinkPreview blank = service.fetch(" ");

    assertThat(p.url()).isEqualTo("ftp://files.example.com/x");
    assertThat(p.title()).isNull();
    assertThat(p.image()).isNull();
    assertThat(file.title()).isNull();
    assertThat(blank.title()).isNull();
    verifyNoInteractions(ogScraper);
  }

  @Test
  void scrapeFailureReturnsBare() {
    when(ogScraper.fetch("https://boom.example")).thenThrow(new RuntimeException("network"));

    LinkPreview p = service.fetch("https://boom.example");

    assertThat(p.url()).isEqualTo("https://boom.example");
    assertThat(p.title()).isNull();
  }

  @Test
  void forgettingRemovesTheCachedPageBeforeReturning() {
    when(caches.getCache("linkPreview")).thenReturn(cache);

    service.forget("https://youtu.be/x");

    verify(cache).evictIfPresent("https://youtu.be/x");
  }

  @Test
  void aMissingCacheOrABrokenRedisLetsTheRefreshGoOn() {
    assertThatCode(() -> service.forget("https://a.example/")).doesNotThrowAnyException();

    when(caches.getCache("linkPreview")).thenReturn(cache);
    doThrow(new IllegalStateException("redis down"))
        .when(cache)
        .evictIfPresent("https://b.example/");

    assertThatCode(() -> service.forget("https://b.example/")).doesNotThrowAnyException();
  }
}
