package com.example.short_link.link.og.presentation;

import com.example.short_link.link.application.LinkLookupService;
import com.example.short_link.link.application.ShortLinkUrlBuilder;
import com.example.short_link.link.og.application.OgCardImageRenderer;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the dynamically-generated OG card PNG for short links that have no destination preview
 * image. Cached aggressively at the edge — the count baked into the image is the human-click total
 * at the time of render, and a 60s freshness window is fine for share previews (they get scraped
 * seconds after the link is posted).
 */
@RestController
@RequiredArgsConstructor
public class OgCardController {

  private final LinkLookupService lookup;
  private final OgCardImageRenderer renderer;
  private final ShortLinkUrlBuilder urlBuilder;

  @GetMapping(value = "/{shortCode:[0-9A-Za-z]{3,16}}/og.png", produces = MediaType.IMAGE_PNG_VALUE)
  public ResponseEntity<byte[]> ogCard(@PathVariable String shortCode) throws IOException {
    var cached = lookup.findActiveLink(shortCode);
    long clicks = lookup.countHumanClicks(cached.linkId());
    byte[] body = renderer.render(urlBuilder.build(shortCode), clicks);
    return ResponseEntity.ok()
        .contentType(MediaType.IMAGE_PNG)
        .header(HttpHeaders.CACHE_CONTROL, "public, max-age=60, s-maxage=60")
        .header("X-Robots-Tag", "noindex")
        .body(body);
  }
}
