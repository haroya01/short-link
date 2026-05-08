package com.example.short_link.link.api;

import com.example.short_link.link.application.CachedLink;
import com.example.short_link.link.application.ClickRecorder;
import com.example.short_link.link.application.LinkLookupService;
import com.example.short_link.link.application.LinkPreviewCrawlerDetector;
import com.example.short_link.link.application.LinkPreviewRenderer;
import com.example.short_link.link.application.ShortLinkUrlBuilder;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RedirectController {

  private final LinkLookupService lookup;
  private final ClickRecorder clickRecorder;
  private final LinkPreviewCrawlerDetector crawlerDetector;
  private final LinkPreviewRenderer previewRenderer;
  private final LinkRepository linkRepository;
  private final ShortLinkUrlBuilder urlBuilder;
  private final MeterRegistry meterRegistry;

  @GetMapping("/{shortCode:[0-9A-Za-z]{7}}")
  public ResponseEntity<?> redirect(
      @PathVariable String shortCode,
      @RequestHeader(value = "Referer", required = false) String referrer,
      @RequestHeader(value = "User-Agent", required = false) String userAgent,
      @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage,
      HttpServletRequest req) {
    CachedLink link = lookup.findActiveLink(shortCode);
    if (crawlerDetector.isCrawler(userAgent)) {
      meterRegistry.counter("short_link.preview").increment();
      LinkEntity entity = linkRepository.findByShortCode(shortCode).orElse(null);
      if (entity == null) {
        return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create(link.originalUrl()))
            .build();
      }
      String html = previewRenderer.render(entity, urlBuilder.build(shortCode));
      byte[] body = html.getBytes(StandardCharsets.UTF_8);
      return ResponseEntity.ok()
          .contentType(MediaType.parseMediaType("text/html; charset=utf-8"))
          .contentLength(body.length)
          .header(HttpHeaders.CACHE_CONTROL, "public, max-age=300")
          .body(body);
    }
    clickRecorder.record(
        link.linkId(), link.originalUrl(), referrer, userAgent, clientIp(req), acceptLanguage);
    return ResponseEntity.status(HttpStatus.FOUND)
        .location(URI.create(link.originalUrl()))
        .header(HttpHeaders.CACHE_CONTROL, "private, max-age=90")
        .build();
  }

  private String clientIp(HttpServletRequest req) {
    String forwarded = req.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",")[0].trim();
    }
    return req.getRemoteAddr();
  }
}
