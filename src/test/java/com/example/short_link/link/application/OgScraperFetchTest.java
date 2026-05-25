package com.example.short_link.link.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.example.short_link.common.net.PublicHttpUrlGuard;
import com.example.short_link.common.net.PublicHttpUrlGuard.Resolved;
import com.example.short_link.link.application.dto.OgMetadata;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
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
    OgMetadata m = runFetch(response(200, ContentType.TEXT_HTML, html));
    assertThat(m.title()).isEqualTo("Hello");
    assertThat(m.description()).isEqualTo("World");
    assertThat(m.image()).isEqualTo("https://example.com/cover.png");
  }

  @Test
  void emptyWhen4xx() {
    OgMetadata m = runFetch(response(404, ContentType.TEXT_HTML, "<html></html>"));
    assertThat(m.hasAny()).isFalse();
  }

  @Test
  void emptyWhen5xx() {
    OgMetadata m = runFetch(response(503, ContentType.TEXT_HTML, "<html></html>"));
    assertThat(m.hasAny()).isFalse();
  }

  @Test
  void emptyWhenNonHtmlContentType() {
    OgMetadata m =
        runFetch(response(200, ContentType.APPLICATION_JSON, "{\"og:title\":\"ignored\"}"));
    assertThat(m.hasAny()).isFalse();
  }

  @Test
  void emptyWhenIoException() {
    OgMetadata m = runFetchThrowing(new IOException("connect reset"));
    assertThat(m.hasAny()).isFalse();
  }

  @Test
  void emptyWhenGuardRejectsUrl() {
    OgScraper scraper = new OgScraper();
    try (MockedStatic<PublicHttpUrlGuard> guard = mockStatic(PublicHttpUrlGuard.class)) {
      guard.when(() -> PublicHttpUrlGuard.resolve(URL)).thenReturn(Optional.empty());
      assertThat(scraper.fetch(URL).hasAny()).isFalse();
    }
  }

  private static OgMetadata runFetch(BasicClassicHttpResponse response) {
    CloseableHttpClient client = mock(CloseableHttpClient.class);
    try {
      when(client.execute(any(ClassicHttpRequest.class), any(HttpClientResponseHandler.class)))
          .thenAnswer(
              inv -> {
                HttpClientResponseHandler<?> handler = inv.getArgument(1);
                return handler.handleResponse(response);
              });
    } catch (IOException e) {
      throw new AssertionError(e);
    }
    return runWith(client);
  }

  private static OgMetadata runFetchThrowing(IOException thrown) {
    CloseableHttpClient client = mock(CloseableHttpClient.class);
    try {
      when(client.execute(any(ClassicHttpRequest.class), any(HttpClientResponseHandler.class)))
          .thenThrow(thrown);
    } catch (IOException e) {
      throw new AssertionError(e);
    }
    return runWith(client);
  }

  private static OgMetadata runWith(CloseableHttpClient client) {
    OgScraper scraper =
        new OgScraper() {
          @Override
          protected CloseableHttpClient buildPinnedClient(Resolved resolved) {
            return client;
          }
        };
    try (MockedStatic<PublicHttpUrlGuard> guard = mockStatic(PublicHttpUrlGuard.class)) {
      Resolved resolved = new Resolved(URI.create(URL), List.<InetAddress>of());
      guard.when(() -> PublicHttpUrlGuard.resolve(URL)).thenReturn(Optional.of(resolved));
      return scraper.fetch(URL);
    }
  }

  private static BasicClassicHttpResponse response(int status, ContentType type, String body) {
    BasicClassicHttpResponse r = new BasicClassicHttpResponse(status);
    r.addHeader("Content-Type", type.toString());
    r.setEntity(new StringEntity(body, type));
    return r;
  }
}
