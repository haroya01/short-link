package com.example.short_link.link.og.application;

import com.example.short_link.link.application.dto.OgMetadata;
import com.example.short_link.link.og.application.dto.LinkPreview;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Non-HTTP(S) URLs and scrape failures return a bare URL card. Results are cached per URL for 24
 * hours.
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
