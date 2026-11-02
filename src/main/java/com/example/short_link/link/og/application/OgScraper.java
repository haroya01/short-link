package com.example.short_link.link.og.application;

import com.example.short_link.common.net.HttpFetcher;
import com.example.short_link.common.net.HttpFetcher.Request;
import com.example.short_link.common.net.HttpFetcher.Response;
import com.example.short_link.common.net.PublicHttpUrlGuard;
import com.example.short_link.common.net.PublicHttpUrlGuard.Resolved;
import com.example.short_link.link.application.dto.OgMetadata;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class OgScraper {

  private static final Duration CONNECT_TIMEOUT = Duration.ofMillis(3_000);
  private static final Duration READ_TIMEOUT = Duration.ofMillis(5_000);
  private static final int MAX_BODY_BYTES = 1_024 * 1_024;
  private static final int MAX_REDIRECTS = 3;
  private static final Set<Integer> REDIRECTS = Set.of(301, 302, 303, 307, 308);
  private static final String USER_AGENT = "kurl-link-preview/1.0 (+https://kurl.me/bot)";

  private final HttpFetcher httpFetcher;
  private final MeterRegistry meterRegistry;

  @Autowired
  public OgScraper(HttpFetcher httpFetcher, MeterRegistry meterRegistry) {
    this.httpFetcher = httpFetcher;
    this.meterRegistry = meterRegistry;
  }

  public OgMetadata fetch(String url) {
    Resolved resolved = PublicHttpUrlGuard.resolve(url).orElse(null);
    if (resolved == null) {
      return OgMetadata.empty();
    }
    Timer.Sample sample = meterRegistry == null ? null : Timer.start(meterRegistry);
    AtomicReference<String> resultTag = new AtomicReference<>("exception");
    try {
      Landing landing = follow(resolved);
      if (landing.refusal() != null) {
        resultTag.set(landing.refusal());
        return OgMetadata.empty();
      }
      Response response = landing.response();
      String baseUrl = landing.uri().toString();
      if (response.status() >= 400) {
        resultTag.set("non_2xx");
        return OgMetadata.empty();
      }
      String contentType = response.header("Content-Type");
      if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("text/html")) {
        resultTag.set("non_html");
        return OgMetadata.empty();
      }
      if (response.body().length == 0) {
        resultTag.set("non_html");
        return OgMetadata.empty();
      }
      Document doc = Jsoup.parse(new String(response.body(), StandardCharsets.UTF_8), baseUrl);
      resultTag.set("ok");
      return parseDocument(doc, baseUrl);
    } catch (RuntimeException e) {
      log.warn("OG fetch unexpected error for {}", url, e);
      return OgMetadata.empty();
    } finally {
      if (sample != null) {
        sample.stop(
            meterRegistry.timer("outbound.http", "client", "og_fetch", "result", resultTag.get()));
      }
    }
  }

  private Landing follow(Resolved first) {
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("User-Agent", USER_AGENT);
    headers.put("Accept", "text/html,application/xhtml+xml");
    headers.put("Accept-Language", "en;q=0.9,ko;q=0.8,ja;q=0.7");
    Resolved current = first;
    Set<URI> visited = new HashSet<>();
    for (int hop = 0; ; hop++) {
      visited.add(current.uri());
      Response response =
          httpFetcher.fetch(
              Request.getNoRedirects(
                  current, headers, CONNECT_TIMEOUT, READ_TIMEOUT, MAX_BODY_BYTES));
      if (!REDIRECTS.contains(response.status())) {
        return new Landing(current.uri(), response, null);
      }
      if (hop == MAX_REDIRECTS) {
        return Landing.refused("too_many_redirects");
      }
      URI next = location(current.uri(), response.header("Location"));
      if (next == null) {
        return Landing.refused("redirect_invalid");
      }
      if (visited.contains(next)) {
        return Landing.refused("redirect_loop");
      }
      Optional<Resolved> target = PublicHttpUrlGuard.resolve(next.toString());
      if (target.isEmpty()) {
        return Landing.refused("redirect_blocked");
      }
      current = target.get();
    }
  }

  private static URI location(URI from, String header) {
    if (header == null || header.isBlank()) {
      return null;
    }
    try {
      return from.resolve(header.strip());
    } catch (IllegalArgumentException malformed) {
      return null;
    }
  }

  private record Landing(URI uri, Response response, String refusal) {

    static Landing refused(String refusal) {
      return new Landing(null, null, refusal);
    }
  }

  static OgMetadata parseDocument(Document doc, String baseUrl) {
    String title = pickFirst(metaContent(doc, "og:title"), title(doc));
    String description =
        pickFirst(metaContent(doc, "og:description"), metaName(doc, "description"));
    String image = absoluteUrl(metaContent(doc, "og:image"), baseUrl);
    return new OgMetadata(truncate(title, 300), truncate(description, 800), truncate(image, 1024));
  }

  static OgMetadata parseHtml(String html, String baseUrl) {
    Document doc = Jsoup.parse(html, baseUrl);
    return parseDocument(doc, baseUrl);
  }

  private static String metaContent(Document doc, String property) {
    Element el = doc.selectFirst("meta[property=" + property + "]");
    if (el == null) {
      el = doc.selectFirst("meta[name=" + property + "]");
    }
    return el == null ? null : el.attr("content");
  }

  private static String metaName(Document doc, String name) {
    Element el = doc.selectFirst("meta[name=" + name + "]");
    return el == null ? null : el.attr("content");
  }

  private static String title(Document doc) {
    String t = doc.title();
    return t == null || t.isBlank() ? null : t;
  }

  private static String pickFirst(String... candidates) {
    for (String c : candidates) {
      if (c != null && !c.isBlank()) return c.trim();
    }
    return null;
  }

  private static String absoluteUrl(String maybeRelative, String baseUrl) {
    if (maybeRelative == null || maybeRelative.isBlank()) return null;
    String trimmed = maybeRelative.trim();
    try {
      URI base = new URI(baseUrl);
      URI resolved = base.resolve(trimmed);
      String scheme = resolved.getScheme();
      if (scheme == null
          || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
        return null;
      }
      return resolved.toString();
    } catch (URISyntaxException | IllegalArgumentException e) {
      return null;
    }
  }

  private static String truncate(String s, int max) {
    if (s == null) return null;
    String trimmed = s.trim();
    if (trimmed.isEmpty()) return null;
    return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
  }
}
