package com.example.short_link.link.application;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Fetches Open Graph metadata from a URL with safety guards: SSRF protection (no private IPs),
 * timeouts, body size limit, content-type whitelist.
 */
@Slf4j
@Service
public class OgScraper {

  private static final int CONNECT_TIMEOUT_MS = 3_000;
  private static final int READ_TIMEOUT_MS = 5_000;
  private static final int MAX_BODY_BYTES = 1_024 * 1_024;
  private static final int MAX_REDIRECTS = 3;
  private static final String USER_AGENT = "kurl-link-preview/1.0 (+https://kurl.me/bot)";

  private final MeterRegistry meterRegistry;

  @Autowired
  public OgScraper(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  /** No-arg constructor for tests that don't care about meter registration. */
  OgScraper() {
    this(null);
  }

  public OgMetadata fetch(String url) {
    if (!isPublicHttpUrl(url)) {
      // Guard rejection still costs a DNS lookup, but no outbound HTTP — don't pollute the
      // outbound.http timer with these.
      return OgMetadata.empty();
    }
    Timer.Sample sample = meterRegistry == null ? null : Timer.start(meterRegistry);
    String result = "exception";
    try {
      Connection conn =
          Jsoup.connect(url)
              .userAgent(USER_AGENT)
              .timeout(CONNECT_TIMEOUT_MS + READ_TIMEOUT_MS)
              .maxBodySize(MAX_BODY_BYTES)
              .followRedirects(true)
              .ignoreContentType(false)
              .ignoreHttpErrors(true)
              .header("Accept", "text/html,application/xhtml+xml")
              .header("Accept-Language", "en;q=0.9,ko;q=0.8,ja;q=0.7");
      Connection.Response response = conn.execute();
      if (response.statusCode() >= 400) {
        result = "non_2xx";
        return OgMetadata.empty();
      }
      String contentType = response.contentType();
      if (contentType == null || !contentType.toLowerCase().startsWith("text/html")) {
        result = "non_html";
        return OgMetadata.empty();
      }
      Document doc = response.parse();
      result = "ok";
      return parseDocument(doc, response.url().toString());
    } catch (IOException e) {
      log.debug("OG fetch failed for {}: {}", url, e.getMessage());
      return OgMetadata.empty();
    } catch (RuntimeException e) {
      log.warn("OG fetch unexpected error for {}", url, e);
      return OgMetadata.empty();
    } finally {
      if (sample != null) {
        sample.stop(meterRegistry.timer("outbound.http", "client", "og_fetch", "result", result));
      }
    }
  }

  /**
   * Extracts OG metadata from a parsed HTML document. Visible for testing so the parsing logic can
   * be exercised without a real HTTP fetch.
   */
  static OgMetadata parseDocument(Document doc, String baseUrl) {
    String title = pickFirst(metaContent(doc, "og:title"), title(doc));
    String description =
        pickFirst(metaContent(doc, "og:description"), metaName(doc, "description"));
    String image = absoluteUrl(metaContent(doc, "og:image"), baseUrl);
    return new OgMetadata(truncate(title, 300), truncate(description, 800), truncate(image, 1024));
  }

  static OgMetadata parseHtml(String html, String baseUrl) {
    Document doc = org.jsoup.Jsoup.parse(html, baseUrl);
    return parseDocument(doc, baseUrl);
  }

  static boolean isPublicHttpUrl(String url) {
    if (url == null || url.isBlank()) return false;
    URI uri;
    try {
      uri = new URI(url);
    } catch (URISyntaxException e) {
      return false;
    }
    String scheme = uri.getScheme();
    if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
      return false;
    }
    String host = uri.getHost();
    if (host == null || host.isBlank()) return false;
    try {
      for (InetAddress addr : InetAddress.getAllByName(host)) {
        if (isPrivateOrLoopback(addr)) {
          return false;
        }
      }
    } catch (UnknownHostException e) {
      return false;
    }
    return true;
  }

  private static boolean isPrivateOrLoopback(InetAddress addr) {
    return addr.isLoopbackAddress()
        || addr.isLinkLocalAddress()
        || addr.isSiteLocalAddress()
        || addr.isAnyLocalAddress()
        || addr.isMulticastAddress();
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

  // Exposed for tests; not used at runtime.
  Duration totalTimeout() {
    return Duration.ofMillis(CONNECT_TIMEOUT_MS + READ_TIMEOUT_MS);
  }
}
