package com.example.short_link.cta.application.write;

import com.example.short_link.cta.exception.CtaErrorCode;
import com.example.short_link.cta.exception.CtaException;
import com.example.short_link.link.application.ShortLinkDetector;
import com.example.short_link.link.application.write.CheckedDestination;
import com.example.short_link.link.application.write.CreateLinkCommand;
import com.example.short_link.link.application.write.CreateLinkUseCase;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
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

  public String trackingCodeFor(Long userId, String url) {
    if (url == null || url.isBlank()) {
      return null;
    }
    String existing = detector.extractCode(url);
    if (existing != null) {
      return existing;
    }
    CreateLinkCommand link = CreateLinkCommand.of(url, userId, null, null);
    CheckedDestination destination;
    try {
      destination = createLink.check(link);
    } catch (LinkException refused) {
      if (refused.errorCode() == LinkErrorCode.MALICIOUS_URL) {
        throw new CtaException(CtaErrorCode.MALICIOUS_URL);
      }
      log.warn("CTA link tracking skipped for user {}: {}", userId, refused.getMessage());
      return null;
    } catch (RuntimeException e) {
      log.warn("CTA link tracking skipped for user {}: {}", userId, e.getMessage());
      return null;
    }
    return createLink
        .tryExecute(link, destination)
        .map(created -> created.shortCode().value())
        .orElse(null);
  }
}
