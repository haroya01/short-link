package com.example.short_link.link.og.application;

import com.example.short_link.link.application.dto.OgMetadata;
import com.example.short_link.link.og.application.dto.LinkPreview;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LinkPreviewService {

  private static final String CACHE = "linkPreview";

  private final OgScraper ogScraper;
  private final CacheManager cacheManager;

  @Cacheable(value = CACHE, key = "#url")
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

  public void forget(String url) {
    try {
      Cache cache = cacheManager.getCache(CACHE);
      if (cache != null) cache.evictIfPresent(url);
    } catch (RuntimeException failure) {
      log.warn("link preview cache eviction failed for {}: {}", url, failure.toString());
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
