package com.example.short_link.link.redirect.presentation;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Uses 301 to consolidate indexing at the frontend URL, shared with OAuth configuration. */
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
