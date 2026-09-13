package com.example.short_link.cta.application.write;

import com.example.short_link.link.application.ShortLinkDetector;
import com.example.short_link.link.application.write.CreateLinkCommand;
import com.example.short_link.link.application.write.CreateLinkUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 추적 링크 생성에 실패해도 CTA 저장은 허용한다. */
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
