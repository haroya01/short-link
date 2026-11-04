package com.example.short_link.link.og.application;

import com.example.short_link.common.net.HttpFetcher;
import com.example.short_link.common.net.HttpFetcher.Response;
import com.example.short_link.common.net.PublicHttpUrlGuard;
import com.example.short_link.common.net.PublicHttpUrlGuard.Resolved;
import com.example.short_link.link.application.dto.OgMetadata;
import com.example.short_link.link.og.application.OgPageFetcher.Landing;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class OgScraper {

  private final OgPageFetcher pages;
  private final MeterRegistry meterRegistry;

  @Autowired
  public OgScraper(HttpFetcher httpFetcher, MeterRegistry meterRegistry) {
    this.pages = new OgPageFetcher(httpFetcher);
    this.meterRegistry = meterRegistry;
  }

  public OgMetadata fetch(String url) {
    Resolved resolved = PublicHttpUrlGuard.resolve(url).orElse(null);
    if (resolved == null) {
      return OgMetadata.empty();
    }
    Timer.Sample sample = meterRegistry == null ? null : Timer.start(meterRegistry);
    String resultTag = "exception";
    try {
      Scraped scraped = scrape(pages.fetch(resolved));
      resultTag = scraped.result();
      return scraped.og();
    } catch (RuntimeException e) {
      log.warn("OG fetch unexpected error for {}", url, e);
      return OgMetadata.empty();
    } finally {
      if (sample != null) {
        sample.stop(
            meterRegistry.timer("outbound.http", "client", "og_fetch", "result", resultTag));
      }
    }
  }

  private static Scraped scrape(Landing landing) {
    if (landing.refusal() != null) {
      return Scraped.empty(landing.refusal());
    }
    Response response = landing.response();
    if (response.status() >= 400) {
      return Scraped.empty("non_2xx");
    }
    String contentType = response.header("Content-Type");
    if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("text/html")) {
      return Scraped.empty("non_html");
    }
    if (response.body().length == 0) {
      return Scraped.empty("non_html");
    }
    String baseUrl = landing.uri().toString();
    Document doc = Jsoup.parse(new String(response.body(), StandardCharsets.UTF_8), baseUrl);
    return new Scraped(parseDocument(doc, baseUrl), "ok");
  }

  private record Scraped(OgMetadata og, String result) {

    static Scraped empty(String result) {
      return new Scraped(OgMetadata.empty(), result);
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
