package com.example.short_link.link.access.presentation;

import com.example.short_link.common.observability.OutcomeResolver;
import com.example.short_link.link.access.application.PasswordUnlockResult;
import com.example.short_link.link.access.application.TurnstileProperties;
import com.example.short_link.link.access.application.write.PasswordUnlockUseCase;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.exception.LinkException;
import com.example.short_link.link.redirect.application.LinkRedirectFlow;
import com.example.short_link.link.redirect.application.RedirectOutcome;
import com.example.short_link.link.redirect.presentation.RedirectController;
import com.example.short_link.link.redirect.presentation.helper.LinkHtmlRenderer;
import com.example.short_link.link.redirect.presentation.helper.LinkRedirectSupport;
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

  private final PasswordUnlockUseCase unlockUseCase;
  private final TurnstileProperties turnstile;

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
      PasswordUnlockResult unlocked =
          unlockUseCase.execute(
              shortCode,
              password,
              captchaToken,
              LinkRedirectSupport.visit(referrer, userAgent, acceptLanguage, src, null, req));
      if (unlocked instanceof PasswordUnlockResult.Rejected rejected) {
        outcome =
            switch (rejected.reason()) {
              case CAPTCHA_FAILED -> "captcha_failed";
              case LOCKED_OUT -> "locked_out";
              case WRONG_PASSWORD -> "password_required";
            };
        HttpStatus status =
            rejected.reason() == PasswordUnlockResult.RejectionReason.LOCKED_OUT
                ? HttpStatus.TOO_MANY_REQUESTS
                : HttpStatus.UNAUTHORIZED;
        boolean failed = rejected.reason() != PasswordUnlockResult.RejectionReason.CAPTCHA_FAILED;
        return LinkHtmlRenderer.passwordPromptResponse(
            status, shortCode, failed, turnstile.siteKey());
      }
      RedirectOutcome result = ((PasswordUnlockResult.Completed) unlocked).redirect();
      ResponseEntity<?> response = renderUnlock(result);
      outcome =
          (result instanceof RedirectOutcome.Blocked
                  || result instanceof RedirectOutcome.DomainBlocked)
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
      case RedirectOutcome.DomainBlocked db -> LinkHtmlRenderer.domainBlockedPageResponse();
      case RedirectOutcome.ExpiredWithMessage em ->
          LinkHtmlRenderer.expiredPageResponse(em.message());
      case RedirectOutcome.PasswordRequired pr ->
          throw new IllegalStateException("PasswordRequired not reachable from unlock flow");
    };
  }
}
