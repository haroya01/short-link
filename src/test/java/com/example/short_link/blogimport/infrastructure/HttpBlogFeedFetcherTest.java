package com.example.short_link.blogimport.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.blogimport.application.BlogFeedFetcher;
import com.example.short_link.blogimport.application.BlogImportProperties;
import com.example.short_link.blogimport.application.HostGate;
import com.example.short_link.blogimport.domain.FeedValidators;
import com.example.short_link.blogimport.exception.BlogImportErrorCode;
import com.example.short_link.blogimport.exception.BlogImportException;
import com.example.short_link.common.net.HttpFetcher;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class HttpBlogFeedFetcherTest {

  private static final String FEED = "https://93.184.216.34/rss";

  private final HttpFetcher http = mock(HttpFetcher.class);
  private final HttpBlogFeedFetcher fetcher =
      new HttpBlogFeedFetcher(http, new HostGate(new BlogImportProperties(null, 0, null, null)));

  private static HttpFetcher.Response response(
      int status, Map<String, String> headers, byte[] body) {
    Map<String, List<String>> multi =
        headers.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> List.of(e.getValue())));
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

  private HttpFetcher.Request lastRequest() {
    ArgumentCaptor<HttpFetcher.Request> request =
        ArgumentCaptor.forClass(HttpFetcher.Request.class);
    verify(http, org.mockito.Mockito.atLeastOnce()).fetch(request.capture());
    return request.getValue();
  }

  @Test
  void readsTheBodyInTheDeclaredCharsetAndKeepsItsValidators() {
    when(http.fetch(any()))
        .thenReturn(
            response(
                200,
                Map.of(
                    "Content-Type", "application/rss+xml; charset=\"EUC-KR\"",
                    "ETag", "\"abc\"",
                    "Last-Modified", "Thu, 15 Oct 2026 00:00:00 GMT"),
                "한글".getBytes(Charset.forName("EUC-KR"))));

    BlogFeedFetcher.Fetched fetched = fetcher.fetch(FEED, FeedValidators.NONE);

    assertThat(fetched.body()).isEqualTo("한글");
    assertThat(fetched.notModified()).isFalse();
    assertThat(fetched.validators())
        .isEqualTo(new FeedValidators("\"abc\"", "Thu, 15 Oct 2026 00:00:00 GMT"));
    HttpFetcher.Request request = lastRequest();
    assertThat(request.followRedirects()).isFalse();
    assertThat(request.maxBodyBytes()).isEqualTo(HttpBlogFeedFetcher.MAX_BYTES + 1);
    assertThat(request.headers().get("User-Agent"))
        .isEqualTo("kurl-importer/1.0 (+https://kurl.me)");
    assertThat(request.headers()).doesNotContainKeys("If-None-Match", "If-Modified-Since");
  }

  @Test
  void asksConditionallyAndAnswersNotModifiedOnA304() {
    when(http.fetch(any())).thenReturn(response(304, Map.of(), new byte[0]));
    FeedValidators stored = new FeedValidators("\"abc\"", "Thu, 15 Oct 2026 00:00:00 GMT");

    BlogFeedFetcher.Fetched fetched = fetcher.fetch(FEED, stored);

    assertThat(fetched.notModified()).isTrue();
    assertThat(fetched.validators()).isEqualTo(stored);
    assertThat(lastRequest().headers())
        .containsEntry("If-None-Match", "\"abc\"")
        .containsEntry("If-Modified-Since", "Thu, 15 Oct 2026 00:00:00 GMT");
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

    assertThat(fetcher.fetch(FEED, FeedValidators.NONE).body()).isEqualTo("<rss/>");
  }

  @Test
  void refusesToFollowARedirectIntoAPrivateNetwork() {
    when(http.fetch(any()))
        .thenReturn(response(302, Map.of("Location", "http://127.0.0.1/admin"), new byte[0]));

    assertThat(codeOf(() -> fetcher.fetch(FEED, FeedValidators.NONE)))
        .isEqualTo(BlogImportErrorCode.IMPORT_SOURCE_UNREACHABLE);
  }

  @Test
  void neverConnectsToAPrivateAddress() {
    assertThat(codeOf(() -> fetcher.fetch("http://10.0.0.1/rss", FeedValidators.NONE)))
        .isEqualTo(BlogImportErrorCode.IMPORT_SOURCE_UNREACHABLE);
    verify(http, never()).fetch(any());
  }

  @Test
  void aRedirectWithoutLocationOrALoopIsUnreachable() {
    when(http.fetch(any())).thenReturn(response(307, Map.of(), new byte[0]));
    assertThat(codeOf(() -> fetcher.fetch(FEED, FeedValidators.NONE)))
        .isEqualTo(BlogImportErrorCode.IMPORT_SOURCE_UNREACHABLE);

    when(http.fetch(any()))
        .thenReturn(response(308, Map.of("Location", "https://93.184.216.34/rss"), new byte[0]));
    assertThat(codeOf(() -> fetcher.fetch(FEED, FeedValidators.NONE)))
        .isEqualTo(BlogImportErrorCode.IMPORT_SOURCE_UNREACHABLE);
  }

  @Test
  void goneFeedsAreNotFoundAndOtherFailuresUnreachable() {
    when(http.fetch(any())).thenReturn(response(404, Map.of(), new byte[0]));
    assertThat(codeOf(() -> fetcher.fetch(FEED, FeedValidators.NONE)))
        .isEqualTo(BlogImportErrorCode.IMPORT_FEED_NOT_FOUND);

    when(http.fetch(any())).thenReturn(response(410, Map.of(), new byte[0]));
    assertThat(codeOf(() -> fetcher.fetch(FEED, FeedValidators.NONE)))
        .isEqualTo(BlogImportErrorCode.IMPORT_FEED_NOT_FOUND);

    when(http.fetch(any())).thenReturn(response(503, Map.of(), new byte[0]));
    assertThat(codeOf(() -> fetcher.fetch(FEED, FeedValidators.NONE)))
        .isEqualTo(BlogImportErrorCode.IMPORT_SOURCE_UNREACHABLE);

    when(http.fetch(any())).thenReturn(response(300, Map.of(), new byte[0]));
    assertThat(codeOf(() -> fetcher.fetch(FEED, FeedValidators.NONE)))
        .isEqualTo(BlogImportErrorCode.IMPORT_SOURCE_UNREACHABLE);

    when(http.fetch(any()))
        .thenReturn(response(200, Map.of(), new byte[HttpBlogFeedFetcher.MAX_BYTES + 1]));
    assertThat(codeOf(() -> fetcher.fetch(FEED, FeedValidators.NONE)))
        .isEqualTo(BlogImportErrorCode.IMPORT_SOURCE_UNREACHABLE);

    when(http.fetch(any())).thenThrow(new IllegalStateException("timeout"));
    assertThatThrownBy(() -> fetcher.fetch(FEED, FeedValidators.NONE))
        .isInstanceOfSatisfying(
            BlogImportException.class,
            e ->
                assertThat(e.errorCode()).isEqualTo(BlogImportErrorCode.IMPORT_SOURCE_UNREACHABLE));
  }

  @Test
  void anUnparseableRedirectIsUnreachable() {
    when(http.fetch(any()))
        .thenReturn(response(301, Map.of("Location", "http://[bad"), new byte[0]));

    assertThat(codeOf(() -> fetcher.fetch(FEED, FeedValidators.NONE)))
        .isEqualTo(BlogImportErrorCode.IMPORT_SOURCE_UNREACHABLE);
  }

  @Test
  void robotsTxtIsReadAndAMissingOneMeansNoRules() {
    when(http.fetch(any()))
        .thenReturn(
            response(
                200, Map.of(), "User-agent: *\nDisallow: /\n".getBytes(StandardCharsets.UTF_8)));
    assertThat(fetcher.robots("https://93.184.216.34")).contains("User-agent: *\nDisallow: /\n");
    assertThat(lastRequest().uri().toString()).isEqualTo("https://93.184.216.34/robots.txt");

    when(http.fetch(any())).thenReturn(response(404, Map.of(), new byte[0]));
    assertThat(fetcher.robots("https://93.184.216.34")).isEmpty();
    when(http.fetch(any())).thenReturn(response(403, Map.of(), new byte[0]));
    assertThat(fetcher.robots("https://93.184.216.34")).isEmpty();
  }

  @Test
  void anUnreadableRobotsTxtStopsTheFetch() {
    when(http.fetch(any())).thenReturn(response(500, Map.of(), new byte[0]));

    assertThat(codeOf(() -> fetcher.robots("https://93.184.216.34")))
        .isEqualTo(BlogImportErrorCode.IMPORT_SOURCE_UNREACHABLE);
  }
}
