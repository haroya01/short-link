package com.example.short_link.blogimport.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.blogimport.exception.BlogImportErrorCode;
import com.example.short_link.blogimport.exception.BlogImportException;
import com.example.short_link.common.net.HttpFetcher;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class HttpBlogFeedFetcherTest {

  private static final String FEED = "https://93.184.216.34/rss";

  private final HttpFetcher http = mock(HttpFetcher.class);
  private final HttpBlogFeedFetcher fetcher = new HttpBlogFeedFetcher(http);

  private static HttpFetcher.Response response(
      int status, Map<String, String> headers, byte[] body) {
    Map<String, List<String>> multi =
        headers.entrySet().stream()
            .collect(
                java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> List.of(e.getValue())));
    return new HttpFetcher.Response(status, multi, body);
  }

  private static BlogImportErrorCode codeOf(Runnable call) {
    try {
      call.run();
    } catch (BlogImportException e) {
      return e.errorCode();
    }
    throw new AssertionError("expected a BlogImportException");
  }

  @Test
  void readsTheBodyInTheDeclaredCharset() {
    when(http.fetch(any()))
        .thenReturn(
            response(
                200,
                Map.of("Content-Type", "application/rss+xml; charset=\"EUC-KR\""),
                "한글".getBytes(java.nio.charset.Charset.forName("EUC-KR"))));

    assertThat(fetcher.fetch(FEED)).isEqualTo("한글");
    ArgumentCaptor<HttpFetcher.Request> request =
        ArgumentCaptor.forClass(HttpFetcher.Request.class);
    verify(http).fetch(request.capture());
    assertThat(request.getValue().followRedirects()).isFalse();
    assertThat(request.getValue().maxBodyBytes()).isEqualTo(HttpBlogFeedFetcher.MAX_BYTES + 1);
    assertThat(request.getValue().headers().get("User-Agent")).startsWith("kurl-blog-import/");
  }

  @Test
  void unknownOrMissingCharsetsFallBackToUtf8() {
    assertThat(HttpBlogFeedFetcher.charset(null)).isEqualTo(StandardCharsets.UTF_8);
    assertThat(HttpBlogFeedFetcher.charset("text/xml")).isEqualTo(StandardCharsets.UTF_8);
    assertThat(HttpBlogFeedFetcher.charset("text/xml; charset=nope-9"))
        .isEqualTo(StandardCharsets.UTF_8);
  }

  @Test
  void followsPublicRedirectsHopByHop() {
    when(http.fetch(any()))
        .thenReturn(response(301, Map.of("Location", "https://1.1.1.1/feed"), new byte[0]))
        .thenReturn(response(200, Map.of(), "<rss/>".getBytes(StandardCharsets.UTF_8)));

    assertThat(fetcher.fetch(FEED)).isEqualTo("<rss/>");
  }

  @Test
  void refusesToFollowARedirectIntoAPrivateNetwork() {
    when(http.fetch(any()))
        .thenReturn(response(302, Map.of("Location", "http://127.0.0.1/admin"), new byte[0]));

    assertThat(codeOf(() -> fetcher.fetch(FEED)))
        .isEqualTo(BlogImportErrorCode.IMPORT_SOURCE_UNREACHABLE);
  }

  @Test
  void neverConnectsToAPrivateAddress() {
    assertThat(codeOf(() -> fetcher.fetch("http://10.0.0.1/rss")))
        .isEqualTo(BlogImportErrorCode.IMPORT_SOURCE_UNREACHABLE);
    verify(http, never()).fetch(any());
  }

  @Test
  void aRedirectWithoutLocationOrALoopIsUnreachable() {
    when(http.fetch(any())).thenReturn(response(307, Map.of(), new byte[0]));
    assertThat(codeOf(() -> fetcher.fetch(FEED)))
        .isEqualTo(BlogImportErrorCode.IMPORT_SOURCE_UNREACHABLE);

    when(http.fetch(any()))
        .thenReturn(response(308, Map.of("Location", "https://93.184.216.34/rss"), new byte[0]));
    assertThat(codeOf(() -> fetcher.fetch(FEED)))
        .isEqualTo(BlogImportErrorCode.IMPORT_SOURCE_UNREACHABLE);
  }

  @Test
  void goneFeedsAreNotFoundAndOtherFailuresUnreachable() {
    when(http.fetch(any())).thenReturn(response(404, Map.of(), new byte[0]));
    assertThat(codeOf(() -> fetcher.fetch(FEED)))
        .isEqualTo(BlogImportErrorCode.IMPORT_FEED_NOT_FOUND);

    when(http.fetch(any())).thenReturn(response(410, Map.of(), new byte[0]));
    assertThat(codeOf(() -> fetcher.fetch(FEED)))
        .isEqualTo(BlogImportErrorCode.IMPORT_FEED_NOT_FOUND);

    when(http.fetch(any())).thenReturn(response(503, Map.of(), new byte[0]));
    assertThat(codeOf(() -> fetcher.fetch(FEED)))
        .isEqualTo(BlogImportErrorCode.IMPORT_SOURCE_UNREACHABLE);

    when(http.fetch(any()))
        .thenReturn(response(200, Map.of(), new byte[HttpBlogFeedFetcher.MAX_BYTES + 1]));
    assertThat(codeOf(() -> fetcher.fetch(FEED)))
        .isEqualTo(BlogImportErrorCode.IMPORT_SOURCE_UNREACHABLE);

    when(http.fetch(any())).thenThrow(new IllegalStateException("timeout"));
    assertThatThrownBy(() -> fetcher.fetch(FEED))
        .isInstanceOfSatisfying(
            BlogImportException.class,
            e ->
                assertThat(e.errorCode()).isEqualTo(BlogImportErrorCode.IMPORT_SOURCE_UNREACHABLE));
  }

  @Test
  void anUnparseableRedirectIsUnreachable() {
    when(http.fetch(any()))
        .thenReturn(response(301, Map.of("Location", "http://[bad"), new byte[0]));

    assertThat(codeOf(() -> fetcher.fetch(FEED)))
        .isEqualTo(BlogImportErrorCode.IMPORT_SOURCE_UNREACHABLE);
  }
}
