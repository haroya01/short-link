package com.example.short_link.link.access.application.write;

import static com.example.short_link.link.access.application.PasswordUnlockResult.RejectionReason.*;

import com.example.short_link.link.access.application.LinkProtectionService;
import com.example.short_link.link.access.application.PasswordAttempts;
import com.example.short_link.link.access.application.PasswordUnlockResult;
import com.example.short_link.link.access.application.TurnstileVerifier;
import com.example.short_link.link.application.dto.CachedLink;
import com.example.short_link.link.application.read.LinkLookupQueryService;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
import com.example.short_link.link.redirect.application.LinkRedirectFlow;
import com.example.short_link.link.redirect.application.RedirectVisit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PasswordUnlockUseCase {
  private final LinkLookupQueryService lookup;
  private final LinkProtectionService protectionService;
  private final PasswordAttempts attemptLimiter;
  private final LinkRedirectFlow flow;
  private final TurnstileVerifier turnstileVerifier;

  public PasswordUnlockResult execute(
      ShortCode shortCode, String password, String captchaToken, RedirectVisit visit) {
    if (turnstileVerifier.enabled() && !turnstileVerifier.verify(captchaToken, null)) {
      return new PasswordUnlockResult.Rejected(CAPTCHA_FAILED);
    }
    String clientIp = visit.clientIp();
    if (attemptLimiter.isLockedOut(shortCode.value(), clientIp)) {
      return new PasswordUnlockResult.Rejected(LOCKED_OUT);
    }
    CachedLink link = lookup.findActiveLink(shortCode);
    LinkEntity entity =
        lookup
            .findEntity(shortCode)
            .orElseThrow(() -> new LinkException(LinkErrorCode.LINK_NOT_FOUND, shortCode));
    if (entity.hasPassword() && !protectionService.checkPassword(entity, password)) {
      attemptLimiter.recordFailure(shortCode.value(), clientIp);
      return new PasswordUnlockResult.Rejected(WRONG_PASSWORD);
    }
    attemptLimiter.reset(shortCode.value(), clientIp);
    return new PasswordUnlockResult.Completed(flow.execute(link, entity, visit));
  }
}
