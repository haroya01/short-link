package com.example.short_link.link.access.presentation;

import com.example.short_link.common.observability.OutcomeResolver;
import com.example.short_link.common.web.ClientIp;
import com.example.short_link.link.access.application.LinkProtectionService;
import com.example.short_link.link.access.application.TurnstileProperties;
import com.example.short_link.link.access.application.TurnstileVerifier;
import com.example.short_link.link.access.infrastructure.LinkPasswordAttemptLimiter;
import com.example.short_link.link.application.dto.CachedLink;
import com.example.short_link.link.application.read.LinkLookupQueryService;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
import com.example.short_link.link.redirect.application.LinkRedirectFlow;
import com.example.short_link.link.redirect.application.RedirectOutcome;
import com.example.short_link.link.redirect.application.helper.LinkHtmlRenderer;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Password-protected unlock — same path / different method from {@link RedirectController}. Checks
 * the password, then hands off to {@link LinkRedirectFlow} for the same post-load pipeline the GET
 * side uses. Failed password renders the prompt at 401; otherwise the outcome renders identically.
 */
@RestController
@RequiredArgsConstructor
public class PasswordUnlockController {

  private final LinkLookupQueryService lookup;
  private final LinkProtectionService protectionService;
  private final LinkPasswordAttemptLimiter attemptLimiter;
  private final LinkRedirectFlow flow;
  private final TurnstileProperties turnstile;
  private final TurnstileVerifier turnstileVerifier;

  @PostMapping(
      value = "/{shortCode:[0-9A-Za-z]{3,16}}",
      consumes = "application/x-www-form-urlencoded")
  public ResponseEntity<?> unlock(
      @PathVariable ShortCode shortCode,
      @RequestParam("password") String password,
      @RequestParam(value = "src", required = false) String src,
      @RequestParam(value = "cf-turnstile-response", required = false) String captchaToken,
      @RequestHeader(value = "Referer", required = false) String referrer,
      @RequestHeader(value = "User-Agent", required = false) String userAgent,
      @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage,
      HttpServletRequest req) {
    String outcome = "error";
    try {
      // 봇 차단(Turnstile)이 켜져 있으면 먼저 통과해야 한다 — 비밀번호 추측 자동화를 막는다.
      if (turnstileVerifier.enabled() && !turnstileVerifier.verify(captchaToken, null)) {
        outcome = "captcha_failed";
        return LinkHtmlRenderer.passwordPromptResponse(
            HttpStatus.UNAUTHORIZED, shortCode, false, turnstile.siteKey());
      }
      String clientIp = ClientIp.of(req);
      // Per-link brute-force lockout: the global per-IP limit is too loose to stop a focused
      // password-guessing run against one short link. Too many misses from one IP = cooldown.
      if (attemptLimiter.isLockedOut(shortCode.value(), clientIp)) {
        outcome = "locked_out";
        return LinkHtmlRenderer.passwordPromptResponse(
            HttpStatus.TOO_MANY_REQUESTS, shortCode, true, turnstile.siteKey());
      }
      CachedLink link = lookup.findActiveLink(shortCode);
      LinkEntity entity =
          lookup
              .findEntity(shortCode)
              .orElseThrow(() -> new LinkException(LinkErrorCode.LINK_NOT_FOUND, shortCode));
      if (entity.hasPassword() && !protectionService.checkPassword(entity, password)) {
        attemptLimiter.recordFailure(shortCode.value(), clientIp);
        outcome = "password_required";
        return LinkHtmlRenderer.passwordPromptResponse(
            HttpStatus.UNAUTHORIZED, shortCode, true, turnstile.siteKey());
      }
      attemptLimiter.reset(shortCode.value(), clientIp);
      RedirectOutcome result =
          flow.execute(link, entity, referrer, userAgent, acceptLanguage, src, req);
      ResponseEntity<?> response = renderUnlock(result);
      outcome =
          (result instanceof RedirectOutcome.Blocked)
              ? "blocked"
              : (result instanceof RedirectOutcome.ExpiredWithMessage) ? "expired" : "redirect";
      return response;
    } catch (LinkException e) {
      outcome =
          switch (e.errorCode()) {
            case LINK_NOT_FOUND -> "not_found";
            case LINK_EXPIRED -> "expired";
            case LINK_VIEW_LIMIT_EXCEEDED -> "view_limit";
            default -> "error";
          };
      // 비밀번호를 맞춰도 한도초과·만료면 JSON 대신 브랜드 HTML 페이지로.
      ResponseEntity<byte[]> page = LinkHtmlRenderer.visitorErrorPage(e.errorCode());
      if (page != null) {
        return page;
      }
      throw e;
    } finally {
      req.setAttribute(OutcomeResolver.ATTRIBUTE, outcome);
    }
  }

  private ResponseEntity<?> renderUnlock(RedirectOutcome outcome) {
    return switch (outcome) {
        // 비밀번호가 맞으면 곧장 302 하지 않고, kurl 마크가 그려지는 잠금 해제 화면을 잠깐 보여준 뒤 이동한다.
      case RedirectOutcome.Redirect r -> LinkHtmlRenderer.unlockedPageResponse(r.picked().url());
      case RedirectOutcome.Blocked b -> LinkHtmlRenderer.blockedPageResponse();
      case RedirectOutcome.ExpiredWithMessage em ->
          LinkHtmlRenderer.expiredPageResponse(em.message());
      case RedirectOutcome.PasswordRequired pr ->
          throw new IllegalStateException("PasswordRequired not reachable from unlock flow");
    };
  }
}
