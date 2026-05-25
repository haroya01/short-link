package com.example.short_link.link.og.application;

import com.example.short_link.common.net.PinnedHttpClientFactory;
import com.example.short_link.common.net.PublicHttpUrlGuard;
import com.example.short_link.common.net.PublicHttpUrlGuard.Resolved;
import com.example.short_link.link.application.dto.OgMetadata;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.util.Timeout;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Fetches Open Graph metadata. Closes the DNS rebinding window via Apache HttpClient 5 with a
 * per-request DnsResolver pinned to the IPs PublicHttpUrlGuard already vetted — same pattern as the
 * webhook dispatcher. Body is capped at {@link #MAX_BODY_BYTES} (read manually, since HC5 doesn't
 * enforce a response size limit out of the box) and parsed with Jsoup as text/html only.
 */
@Slf4j
@Service
public class OgScraper {

  private static final int CONNECT_TIMEOUT_MS = 3_000;
  private static final int READ_TIMEOUT_MS = 5_000;
  private static final int MAX_BODY_BYTES = 1_024 * 1_024;
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
    Resolved resolved = PublicHttpUrlGuard.resolve(url).orElse(null);
    if (resolved == null) {
      return OgMetadata.empty();
    }
    Timer.Sample sample = meterRegistry == null ? null : Timer.start(meterRegistry);
    AtomicReference<String> resultTag = new AtomicReference<>("exception");
    String baseUrl = resolved.uri().toString();
    try (CloseableHttpClient client = buildPinnedClient(resolved)) {
      HttpGet get = new HttpGet(resolved.uri());
      get.setHeader("User-Agent", USER_AGENT);
      get.setHeader("Accept", "text/html,application/xhtml+xml");
      get.setHeader("Accept-Language", "en;q=0.9,ko;q=0.8,ja;q=0.7");
      return client.execute(
          get,
          response -> {
            int code = response.getCode();
            if (code >= 400) {
              resultTag.set("non_2xx");
              return OgMetadata.empty();
            }
            Header ct = response.getFirstHeader("Content-Type");
            if (ct == null || !ct.getValue().toLowerCase().startsWith("text/html")) {
              resultTag.set("non_html");
              return OgMetadata.empty();
            }
            if (response.getEntity() == null) {
              resultTag.set("non_html");
              return OgMetadata.empty();
            }
            byte[] body;
            try (InputStream in = response.getEntity().getContent()) {
              body = readUpTo(in, MAX_BODY_BYTES);
            }
            Document doc = Jsoup.parse(new String(body, StandardCharsets.UTF_8), baseUrl);
            resultTag.set("ok");
            return parseDocument(doc, baseUrl);
          });
    } catch (IOException e) {
      log.debug("OG fetch failed for {}: {}", url, e.getMessage());
      return OgMetadata.empty();
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

  /** Visible for tests. */
  protected CloseableHttpClient buildPinnedClient(Resolved resolved) {
    return PinnedHttpClientFactory.build(
        resolved,
        Timeout.ofMilliseconds(CONNECT_TIMEOUT_MS),
        Timeout.ofMilliseconds(READ_TIMEOUT_MS),
        Timeout.ofMilliseconds(CONNECT_TIMEOUT_MS + READ_TIMEOUT_MS));
  }

  private static byte[] readUpTo(InputStream in, int max) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(max, 8192));
    byte[] buf = new byte[8192];
    int total = 0;
    while (total < max) {
      int n = in.read(buf, 0, Math.min(buf.length, max - total));
      if (n <= 0) break;
      out.write(buf, 0, n);
      total += n;
    }
    return out.toByteArray();
  }

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
