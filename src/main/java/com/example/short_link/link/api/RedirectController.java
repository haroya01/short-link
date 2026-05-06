package com.example.short_link.link.api;

import com.example.short_link.link.application.CachedLink;
import com.example.short_link.link.application.ClickRecorder;
import com.example.short_link.link.application.LinkLookupService;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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

  @GetMapping("/{shortCode:[0-9A-Za-z]{7}}")
  public ResponseEntity<Void> redirect(
      @PathVariable String shortCode,
      @RequestHeader(value = "Referer", required = false) String referrer,
      @RequestHeader(value = "User-Agent", required = false) String userAgent,
      HttpServletRequest req) {
    CachedLink link = lookup.findActiveLink(shortCode);
    clickRecorder.record(link.linkId(), link.originalUrl(), referrer, userAgent, clientIp(req));
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
