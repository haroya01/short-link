package com.example.short_link.link.og.application;

import com.example.short_link.link.application.dto.OgMetadata;
import com.example.short_link.link.og.application.dto.LinkPreview;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Unfurls an arbitrary URL into a {@link LinkPreview} (og:title/description/image) for blog link
 * cards — reusing the existing {@link OgScraper} (SSRF-safe {@code HttpFetcher}). Cached 24h per
 * URL so the same link doesn't re-fetch on every post render. Non-http(s) or scrape failures
 * collapse to a bare card (just the URL) so the frontend always has something to draw.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LinkPreviewService {

  private final OgScraper ogScraper;

  @Cacheable(value = "linkPreview", key = "#url")
  public LinkPreview fetch(String url) {
    if (!isHttp(url)) return LinkPreview.bare(url);
    try {
      OgMetadata og = ogScraper.fetch(url);
      return new LinkPreview(url, og.title(), og.description(), og.image());
    } catch (RuntimeException e) {
      log.warn("link preview fetch failed for {}: {}", url, e.getMessage());
      return LinkPreview.bare(url);
    }
  }

  private static boolean isHttp(String url) {
    if (url == null || url.isBlank()) return false;
    try {
      String scheme = URI.create(url.trim()).getScheme();
      return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    } catch (RuntimeException e) {
      return false;
    }
  }
}
