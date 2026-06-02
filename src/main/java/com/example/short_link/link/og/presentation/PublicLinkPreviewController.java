package com.example.short_link.link.og.presentation;

import com.example.short_link.link.og.application.LinkPreviewService;
import com.example.short_link.link.og.application.dto.LinkPreview;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public link-preview (unfurl) — the blog editor calls this to draw a velog-style card when a URL
 * is pasted, and the published post renders the same card. SSRF-safe + 24h cached in {@link
 * LinkPreviewService}.
 */
@RestController
@RequestMapping("/api/v1/public/link-preview")
@RequiredArgsConstructor
public class PublicLinkPreviewController {

  private final LinkPreviewService linkPreviewService;

  @GetMapping
  public LinkPreview preview(@RequestParam String url) {
    return linkPreviewService.fetch(url);
  }
}
