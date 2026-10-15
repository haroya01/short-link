package com.example.short_link.blogimport.infrastructure;

import com.example.short_link.blogimport.application.BlogFeedFetcher;
import com.example.short_link.blogimport.application.HostGate;
import com.example.short_link.blogimport.domain.FeedValidators;
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
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// Every hop, redirects included, is checked by PublicHttpUrlGuard and connects only to the
// addresses it resolved, so a feed cannot point the server at a private network.
@Slf4j
@Component
@RequiredArgsConstructor
public class HttpBlogFeedFetcher implements BlogFeedFetcher {

  public static final String USER_AGENT = "kurl-importer/1.0 (+https://kurl.me)";
  static final int MAX_BYTES = 8 * 1024 * 1024;
  static final int MAX_ROBOTS_BYTES = 512 * 1024;
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);
  private static final int MAX_REDIRECT_HOPS = 5;

  private final HttpFetcher httpFetcher;
  private final HostGate hosts;

  @Override
  public Fetched fetch(String url, FeedValidators validators) {
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("User-Agent", USER_AGENT);
    headers.put(
        "Accept", "application/rss+xml, application/xml;q=0.9, text/xml;q=0.9, text/html;q=0.8");
    if (validators != null && validators.etag() != null) {
      headers.put("If-None-Match", validators.etag());
    }
    if (validators != null && validators.lastModified() != null) {
      headers.put("If-Modified-Since", validators.lastModified());
    }
    Response response = hosts.call(url, () -> exchange(url, headers, MAX_BYTES + 1));
    int status = response.status();
    FeedValidators next =
        new FeedValidators(response.header("ETag"), response.header("Last-Modified"));
    if (status == 304) {
      return Fetched.notModified(validators);
    }
    if (status == 404 || status == 410) {
      throw new BlogImportException(BlogImportErrorCode.IMPORT_FEED_NOT_FOUND, url);
    }
    if (status >= 300 || response.body().length > MAX_BYTES) {
      throw new BlogImportException(BlogImportErrorCode.IMPORT_SOURCE_UNREACHABLE, url);
    }
    return new Fetched(
        new String(response.body(), charset(response.header("Content-Type"))), next, false);
  }

  @Override
  public Optional<String> robots(String origin) {
    String url = origin + "/robots.txt";
    Map<String, String> headers = Map.of("User-Agent", USER_AGENT, "Accept", "text/plain");
    Response response = hosts.call(url, () -> exchange(url, headers, MAX_ROBOTS_BYTES));
    int status = response.status();
    if (status >= 400 && status < 500) {
      return Optional.empty();
    }
    if (status != 200) {
      throw new BlogImportException(BlogImportErrorCode.IMPORT_SOURCE_UNREACHABLE, url);
    }
    return Optional.of(new String(response.body(), StandardCharsets.UTF_8));
  }

  private Response exchange(String url, Map<String, String> headers, int maxBytes) {
    Resolved current = guard(url);
    for (int hop = 0; hop <= MAX_REDIRECT_HOPS; hop++) {
      Response response;
      try {
        response =
            httpFetcher.fetch(
                Request.getNoRedirects(current, headers, CONNECT_TIMEOUT, READ_TIMEOUT, maxBytes));
      } catch (RuntimeException e) {
        log.warn("blog feed fetch failed url={}", url, e);
        throw new BlogImportException(BlogImportErrorCode.IMPORT_SOURCE_UNREACHABLE, url);
      }
      int status = response.status();
      if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
        current = next(current, response.header("Location"), url);
        continue;
      }
      return response;
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
