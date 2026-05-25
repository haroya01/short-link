package com.example.short_link.link.og.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.example.short_link.common.net.HttpFetcher;
import com.example.short_link.common.net.PublicHttpUrlGuard;
import com.example.short_link.common.net.PublicHttpUrlGuard.Resolved;
import com.example.short_link.link.application.dto.OgMetadata;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class OgScraperFetchTest {

  private static final String URL = "https://example.com/article";

  @Test
  void parsesHtmlOnSuccess() {
    String html =
        """
        <html><head>
          <meta property="og:title" content="Hello">
          <meta property="og:description" content="World">
          <meta property="og:image" content="https://example.com/cover.png">
        </head></html>
        """;
    OgMetadata m = runFetch(response(200, "text/html; charset=utf-8", html));
    assertThat(m.title()).isEqualTo("Hello");
    assertThat(m.description()).isEqualTo("World");
    assertThat(m.image()).isEqualTo("https://example.com/cover.png");
  }

  @Test
  void emptyWhen4xx() {
    OgMetadata m = runFetch(response(404, "text/html", "<html></html>"));
    assertThat(m.hasAny()).isFalse();
  }

  @Test
  void emptyWhen5xx() {
    OgMetadata m = runFetch(response(503, "text/html", "<html></html>"));
    assertThat(m.hasAny()).isFalse();
  }

  @Test
  void emptyWhenNonHtmlContentType() {
    OgMetadata m = runFetch(response(200, "application/json", "{\"og:title\":\"ignored\"}"));
    assertThat(m.hasAny()).isFalse();
  }

  @Test
  void emptyWhenIoException() {
    HttpFetcher fetcher = mock(HttpFetcher.class);
    when(fetcher.fetch(any(HttpFetcher.Request.class)))
        .thenThrow(new UncheckedIOException(new IOException("connect reset")));
    assertThat(runWith(fetcher).hasAny()).isFalse();
  }

  @Test
  void emptyWhenGuardRejectsUrl() {
    HttpFetcher fetcher = mock(HttpFetcher.class);
    OgScraper scraper = new OgScraper(fetcher, null);
    try (MockedStatic<PublicHttpUrlGuard> guard = mockStatic(PublicHttpUrlGuard.class)) {
      guard.when(() -> PublicHttpUrlGuard.resolve(URL)).thenReturn(Optional.empty());
      assertThat(scraper.fetch(URL).hasAny()).isFalse();
    }
  }

  private static OgMetadata runFetch(HttpFetcher.Response response) {
    HttpFetcher fetcher = mock(HttpFetcher.class);
    when(fetcher.fetch(any(HttpFetcher.Request.class))).thenReturn(response);
    return runWith(fetcher);
  }

  private static OgMetadata runWith(HttpFetcher fetcher) {
    OgScraper scraper = new OgScraper(fetcher, null);
    try (MockedStatic<PublicHttpUrlGuard> guard = mockStatic(PublicHttpUrlGuard.class)) {
      Resolved resolved = new Resolved(URI.create(URL), List.<InetAddress>of());
      guard.when(() -> PublicHttpUrlGuard.resolve(URL)).thenReturn(Optional.of(resolved));
      return scraper.fetch(URL);
    }
  }

  private static HttpFetcher.Response response(int status, String contentType, String body) {
    return new HttpFetcher.Response(
        status,
        Map.of("Content-Type", List.of(contentType)),
        body.getBytes(StandardCharsets.UTF_8));
  }
}
