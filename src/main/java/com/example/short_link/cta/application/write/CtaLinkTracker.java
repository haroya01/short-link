package com.example.short_link.cta.application.write;

import com.example.short_link.link.application.ShortLinkDetector;
import com.example.short_link.link.application.write.CheckedDestination;
import com.example.short_link.link.application.write.CreateLinkCommand;
import com.example.short_link.link.application.write.CreateLinkUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// 추적 링크 생성에 실패해도 CTA 저장은 허용한다.
@Slf4j
@Component
@RequiredArgsConstructor
public class CtaLinkTracker {

  private final CreateLinkUseCase createLink;
  private final ShortLinkDetector detector;

  public record Tracking(
      String existingCode, CreateLinkCommand link, CheckedDestination destination) {
    public static final Tracking NONE = new Tracking(null, null, null);
  }

  public Tracking plan(Long userId, String url) {
    if (url == null || url.isBlank()) {
      return Tracking.NONE;
    }
    String existing = detector.extractCode(url);
    if (existing != null) {
      return new Tracking(existing, null, null); // already a kurl link — measured as-is
    }
    CreateLinkCommand link = CreateLinkCommand.of(url, userId, null, null);
    try {
      return new Tracking(null, link, createLink.check(link));
    } catch (RuntimeException e) {
      log.warn("CTA link tracking skipped for user {}: {}", userId, e.getMessage());
      return Tracking.NONE;
    }
  }

  public String trackingCodeFor(Tracking tracking) {
    if (tracking.existingCode() != null) {
      return tracking.existingCode();
    }
    if (tracking.destination() == null) {
      return null;
    }
    try {
      return createLink.execute(tracking.link(), tracking.destination()).shortCode().value();
    } catch (RuntimeException e) {
      log.warn(
          "CTA link tracking skipped for user {}: {}", tracking.link().userId(), e.getMessage());
      return null;
    }
  }
}
