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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

class OgScraperFetchTest {

  private static final String URL = "https://example.com/article";
  private static final String SHARE = "https://youtu.be/dQw4w9WgXcQ?si=abc";
  private static final String PUBLIC_IP = "https://203.0.113.10/s/dQw4w9WgXcQ";
  private static final String WATCH =
      "https://www.youtube.com/watch?si=abc&v=dQw4w9WgXcQ&feature=youtu.be";
  private static final String WATCH_HTML =
      """
      <html><head>
        <meta property="og:title" content="Never Gonna Give You Up">
        <meta property="og:description" content="Rick Astley">
        <meta property="og:image" content="/vi/dQw4w9WgXcQ/maxresdefault.jpg">
      </head></html>
      """;

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

  @Test
  void followsShareLinkRedirectToTheWatchPage() {
    Pages pages =
        new Pages().redirect(SHARE, 303, WATCH).page(WATCH, response(200, "text/html", WATCH_HTML));

    Fetched fetched = fetch(pages, SHARE, List.of(SHARE, WATCH));

    assertThat(fetched.og().title()).isEqualTo("Never Gonna Give You Up");
    assertThat(fetched.og().description()).isEqualTo("Rick Astley");
    assertThat(fetched.og().image())
        .isEqualTo("https://www.youtube.com/vi/dQw4w9WgXcQ/maxresdefault.jpg");
    assertThat(fetched.result()).isEqualTo("ok");
    assertThat(pages.requested()).containsExactly(SHARE, WATCH);
    assertThat(pages.requests)
        .allSatisfy(
            request -> {
              assertThat(request.followRedirects()).isFalse();
              assertThat(request.maxBodyBytes()).isEqualTo(1_024 * 1_024);
            });
  }

  @ParameterizedTest
  @ValueSource(ints = {301, 302, 307, 308})
  void followsEveryRedirectStatus(int status) {
    Pages pages =
        new Pages()
            .redirect(SHARE, status, WATCH)
            .page(WATCH, response(200, "text/html", WATCH_HTML));

    assertThat(fetch(pages, SHARE, List.of(SHARE, WATCH)).og().title())
        .isEqualTo("Never Gonna Give You Up");
  }

  @Test
  void resolvesARelativeLocationAgainstTheHopThatSentIt() {
    String apex = "https://example.com/a";
    String www = "https://www.example.com/posts/2026/draft";
    String published = "https://www.example.com/posts/2026/final?ref=old";
    Pages pages =
        new Pages()
            .redirect(apex, 301, www)
            .redirect(www, 302, "final?ref=old")
            .page(published, response(200, "text/html", WATCH_HTML));

    Fetched fetched = fetch(pages, apex, List.of(apex, www, published));

    assertThat(pages.requested()).containsExactly(apex, www, published);
    assertThat(fetched.og().image())
        .isEqualTo("https://www.example.com/vi/dQw4w9WgXcQ/maxresdefault.jpg");
  }

  @Test
  void followsThreeRedirects() {
    Pages pages =
        new Pages()
            .redirect("https://a.example/", 301, "https://b.example/")
            .redirect("https://b.example/", 301, "https://c.example/")
            .redirect("https://c.example/", 301, "https://d.example/")
            .page("https://d.example/", response(200, "text/html", WATCH_HTML));

    Fetched fetched =
        fetch(
            pages,
            "https://a.example/",
            List.of(
                "https://a.example/",
                "https://b.example/",
                "https://c.example/",
                "https://d.example/"));

    assertThat(fetched.result()).isEqualTo("ok");
    assertThat(fetched.og().title()).isEqualTo("Never Gonna Give You Up");
  }

  @Test
  void refusesAFourthRedirect() {
    Pages pages =
        new Pages()
            .redirect("https://a.example/", 301, "https://b.example/")
            .redirect("https://b.example/", 301, "https://c.example/")
            .redirect("https://c.example/", 301, "https://d.example/")
            .redirect("https://d.example/", 301, "https://e.example/");

    Fetched fetched =
        fetch(
            pages,
            "https://a.example/",
            List.of(
                "https://a.example/",
                "https://b.example/",
                "https://c.example/",
                "https://d.example/",
                "https://e.example/"));

    assertThat(fetched.og().hasAny()).isFalse();
    assertThat(fetched.result()).isEqualTo("too_many_redirects");
    assertThat(pages.requested())
        .containsExactly(
            "https://a.example/", "https://b.example/", "https://c.example/", "https://d.example/");
  }

  @Test
  void followsARedirectBetweenPublicAddressesThroughTheRealGuard() {
    String next = "https://198.51.100.7/watch?v=dQw4w9WgXcQ";
    Pages pages =
        new Pages()
            .redirect(PUBLIC_IP, 302, next)
            .page(next, response(200, "text/html", WATCH_HTML));

    Fetched fetched = scrape(pages, PUBLIC_IP);

    assertThat(fetched.result()).isEqualTo("ok");
    assertThat(pages.requested()).containsExactly(PUBLIC_IP, next);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "http://169.254.169.254/latest/meta-data/iam/security-credentials/",
        "http://127.0.0.1:8080/actuator/env",
        "http://10.0.0.5/",
        "http://192.168.1.1/",
        "http://[::1]/",
        "http://[::ffff:10.0.0.1]/"
      })
  void refusesARedirectIntoTheInternalNetwork(String internal) {
    Pages pages = new Pages().redirect(PUBLIC_IP, 302, internal);

    Fetched fetched = scrape(pages, PUBLIC_IP);

    assertThat(fetched.og().hasAny()).isFalse();
    assertThat(fetched.result()).isEqualTo("redirect_blocked");
    assertThat(pages.requested()).containsExactly(PUBLIC_IP);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "file:///etc/passwd",
        "ftp://example.com/pub",
        "gopher://example.com:70/_",
        "javascript:alert(1)"
      })
  void refusesARedirectToANonHttpScheme(String target) {
    Pages pages = new Pages().redirect(PUBLIC_IP, 302, target);

    Fetched fetched = scrape(pages, PUBLIC_IP);

    assertThat(fetched.og().hasAny()).isFalse();
    assertThat(fetched.result()).isEqualTo("redirect_blocked");
    assertThat(pages.requested()).containsExactly(PUBLIC_IP);
  }

  @Test
  void stopsAtARedirectLoop() {
    Pages pages = new Pages().redirect(SHARE, 302, WATCH).redirect(WATCH, 302, SHARE);

    Fetched fetched = fetch(pages, SHARE, List.of(SHARE, WATCH));

    assertThat(fetched.og().hasAny()).isFalse();
    assertThat(fetched.result()).isEqualTo("redirect_loop");
    assertThat(pages.requested()).containsExactly(SHARE, WATCH);
  }

  @Test
  void stopsAtARedirectToItself() {
    Pages pages = new Pages().redirect(SHARE, 307, "/dQw4w9WgXcQ?si=abc");

    Fetched fetched = fetch(pages, SHARE, List.of(SHARE));

    assertThat(fetched.result()).isEqualTo("redirect_loop");
    assertThat(pages.requested()).containsExactly(SHARE);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"https://www.you tube.com/watch"})
  void refusesARedirectWithoutAUsableLocation(String location) {
    Pages pages = new Pages().redirect(SHARE, 302, location);

    Fetched fetched = fetch(pages, SHARE, List.of(SHARE));

    assertThat(fetched.og().hasAny()).isFalse();
    assertThat(fetched.result()).isEqualTo("redirect_invalid");
    assertThat(pages.requested()).containsExactly(SHARE);
  }

  static Stream<Arguments> answers() {
    String titled = "<html><head><meta property=\"og:title\" content=\"Hello\"></head></html>";
    return Stream.of(
        Arguments.of("a 400 that still sends a page", 400, "text/html", titled, "non_2xx", null),
        Arguments.of("a page served as JSON", 200, "application/json", titled, "non_html", null),
        Arguments.of("a page without a type", 200, null, titled, "non_html", null),
        Arguments.of("an empty HTML answer", 200, "text/html", "", "non_html", null),
        Arguments.of(
            "HTML named in capitals", 200, "TEXT/HTML; charset=UTF-8", titled, "ok", "Hello"));
  }

  @ParameterizedTest(name = "{0} is tagged {4}")
  @MethodSource("answers")
  void eachAnswerIsTaggedByWhatCameBackAndOnlyAnHtmlPageIsRead(
      String answer, int status, String type, String body, String tag, String title) {
    Map<String, List<String>> headers =
        type == null ? Map.of() : Map.of("Content-Type", List.of(type));
    Pages pages =
        new Pages()
            .page(
                URL,
                new HttpFetcher.Response(status, headers, body.getBytes(StandardCharsets.UTF_8)));

    Fetched fetched = fetch(pages, URL, List.of(URL));

    assertThat(fetched.result()).as(answer).isEqualTo(tag);
    assertThat(fetched.og().title()).as(answer).isEqualTo(title);
  }

  private static Fetched fetch(Pages pages, String url, List<String> publicUrls) {
    try (MockedStatic<PublicHttpUrlGuard> guard = mockStatic(PublicHttpUrlGuard.class)) {
      for (String publicUrl : publicUrls) {
        Resolved resolved = new Resolved(URI.create(publicUrl), List.<InetAddress>of());
        guard.when(() -> PublicHttpUrlGuard.resolve(publicUrl)).thenReturn(Optional.of(resolved));
      }
      return scrape(pages, url);
    }
  }

  private static Fetched scrape(Pages pages, String url) {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    OgMetadata og = new OgScraper(pages, registry).fetch(url);
    String result =
        registry.find("outbound.http").tag("client", "og_fetch").timer().getId().getTag("result");
    return new Fetched(og, result);
  }

  private record Fetched(OgMetadata og, String result) {}

  private static final class Pages implements HttpFetcher {

    private final Map<URI, Response> responses = new HashMap<>();
    private final List<Request> requests = new ArrayList<>();

    Pages page(String url, Response response) {
      responses.put(URI.create(url), response);
      return this;
    }

    Pages redirect(String url, int status, String location) {
      Map<String, List<String>> headers =
          location == null ? Map.of() : Map.of("Location", List.of(location));
      return page(url, new Response(status, headers, new byte[0]));
    }

    List<String> requested() {
      return requests.stream().map(request -> request.uri().toString()).toList();
    }

    @Override
    public Response fetch(Request request) {
      requests.add(request);
      Response response = responses.get(request.uri());
      if (response == null) {
        throw new AssertionError("fetched an unexpected address " + request.uri());
      }
      return response;
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
