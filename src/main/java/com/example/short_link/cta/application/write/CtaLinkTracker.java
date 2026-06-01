package com.example.short_link.cta.application.write;

import com.example.short_link.link.application.ShortLinkDetector;
import com.example.short_link.link.application.write.CreateLinkCommand;
import com.example.short_link.link.application.write.CreateLinkUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Makes a CTA's target measurable by ensuring it resolves to a kurl short link: an external URL is
 * wrapped into a short link (deduped per user, so editing the same CTA reuses one), an existing
 * kurl link is taken as-is. The returned short code is stored on the CTA and served from the public
 * post, so clicks flow through the redirect and attribute to the post ("이 글이 만든 클릭"). Best-effort:
 * if link creation fails (e.g. the user's link quota is full), tracking is skipped rather than
 * blocking the CTA save.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CtaLinkTracker {

  private final CreateLinkUseCase createLink;
  private final ShortLinkDetector detector;

  /**
   * Returns the short code to track this CTA url by, or {@code null} if tracking isn't possible.
   */
  public String trackingCodeFor(Long userId, String url) {
    if (url == null || url.isBlank()) {
      return null;
    }
    String existing = detector.extractCode(url);
    if (existing != null) {
      return existing; // already a kurl link — measured as-is, don't wrap again
    }
    try {
      return createLink.execute(CreateLinkCommand.of(url, userId, null, null)).shortCode().value();
    } catch (RuntimeException e) {
      log.warn("CTA link tracking skipped for user {}: {}", userId, e.getMessage());
      return null;
    }
  }
}
