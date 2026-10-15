package com.example.short_link.blogimport.infrastructure;

import com.example.short_link.blogimport.application.BlogFeedFetcher;
import com.example.short_link.blogimport.exception.BlogImportErrorCode;
import com.example.short_link.blogimport.exception.BlogImportException;
import com.example.short_link.common.net.HttpFetcher;
import com.example.short_link.common.net.HttpFetcher.Request;
import com.example.short_link.common.net.HttpFetcher.Response;
import com.example.short_link.common.net.PublicHttpUrlGuard;
import com.example.short_link.common.net.PublicHttpUrlGuard.Resolved;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// Every hop, redirects included, is checked by PublicHttpUrlGuard and connects only to the
// addresses it resolved, so a feed cannot point the server at a private network.
@Slf4j
@Component
@RequiredArgsConstructor
public class HttpBlogFeedFetcher implements BlogFeedFetcher {

  static final int MAX_BYTES = 8 * 1024 * 1024;
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);
  private static final int MAX_REDIRECT_HOPS = 5;
  private static final Map<String, String> HEADERS =
      Map.of(
          "User-Agent", "kurl-blog-import/1.0 (+https://kurl.me/bot)",
          "Accept", "application/rss+xml, application/xml;q=0.9, text/xml;q=0.9, text/html;q=0.8");

  private final HttpFetcher httpFetcher;

  @Override
  public String fetch(String url) {
    Resolved current = guard(url);
    for (int hop = 0; hop <= MAX_REDIRECT_HOPS; hop++) {
      Response response;
      try {
        response =
            httpFetcher.fetch(
                Request.getNoRedirects(
                    current, HEADERS, CONNECT_TIMEOUT, READ_TIMEOUT, MAX_BYTES + 1));
      } catch (RuntimeException e) {
        log.warn("blog feed fetch failed url={}", url, e);
        throw new BlogImportException(BlogImportErrorCode.IMPORT_SOURCE_UNREACHABLE, url);
      }
      int status = response.status();
      if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
        current = next(current, response.header("Location"), url);
        continue;
      }
      if (status == 404 || status == 410) {
        throw new BlogImportException(BlogImportErrorCode.IMPORT_FEED_NOT_FOUND, url);
      }
      if (status >= 400 || response.body().length > MAX_BYTES) {
        throw new BlogImportException(BlogImportErrorCode.IMPORT_SOURCE_UNREACHABLE, url);
      }
      return new String(response.body(), charset(response.header("Content-Type")));
    }
    throw new BlogImportException(BlogImportErrorCode.IMPORT_SOURCE_UNREACHABLE, url);
  }

  private static Resolved next(Resolved current, String location, String url) {
    if (location == null || location.isBlank()) {
      throw new BlogImportException(BlogImportErrorCode.IMPORT_SOURCE_UNREACHABLE, url);
    }
    try {
      return guard(current.uri().resolve(location.strip()).toString());
    } catch (IllegalArgumentException e) {
      throw new BlogImportException(BlogImportErrorCode.IMPORT_SOURCE_UNREACHABLE, url);
    }
  }

  private static Resolved guard(String url) {
    return PublicHttpUrlGuard.resolve(url)
        .orElseThrow(
            () -> new BlogImportException(BlogImportErrorCode.IMPORT_SOURCE_UNREACHABLE, url));
  }

  static Charset charset(String contentType) {
    if (contentType != null) {
      for (String part : contentType.split(";")) {
        String value = part.strip();
        if (value.toLowerCase(Locale.ROOT).startsWith("charset=")) {
          try {
            return Charset.forName(value.substring("charset=".length()).replace("\"", "").strip());
          } catch (RuntimeException e) {
            return StandardCharsets.UTF_8;
          }
        }
      }
    }
    return StandardCharsets.UTF_8;
  }
}
