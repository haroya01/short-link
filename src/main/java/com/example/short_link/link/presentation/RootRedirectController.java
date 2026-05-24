package com.example.short_link.link.presentation;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Root path on the apex (e.g., {@code https://kurl.me/}) bounces visitors to the frontend so the
 * apex stays clean for short URLs (kurl.me/abc) without serving a blank 404. The redirect target is
 * the same value the OAuth flow uses, so there's only one source of truth for "where the SPA
 * lives".
 *
 * <p>Uses HTTP 301 (Moved Permanently) so Google Search consolidates ranking signals onto {@code
 * app.kurl.me} instead of leaving the apex stuck in "crawled — not indexed" purgatory (which is
 * what 302 produces for SPA bounces).
 */
@RestController
public class RootRedirectController {

  @Value("${short-link.frontend-base-url}")
  private String frontendBaseUrl;

  @GetMapping("/")
  public void root(HttpServletResponse res) {
    res.setStatus(HttpStatus.MOVED_PERMANENTLY.value());
    res.setHeader("Location", frontendBaseUrl);
  }
}
