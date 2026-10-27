package com.example.short_link.link.access.presentation;

import com.example.short_link.common.observability.OutcomeResolver;
import com.example.short_link.common.user.OpenToSuspended;
import com.example.short_link.link.access.application.PasswordUnlockResult;
import com.example.short_link.link.access.application.TurnstileProperties;
import com.example.short_link.link.access.application.write.PasswordUnlockUseCase;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.exception.LinkException;
import com.example.short_link.link.redirect.application.RedirectOutcome;
import com.example.short_link.link.redirect.presentation.helper.LinkHtmlRenderer;
import com.example.short_link.link.redirect.presentation.helper.LinkRedirectSupport;
import com.example.short_link.link.redirect.presentation.helper.VisitResponses;
import com.example.short_link.link.redirect.presentation.helper.VisitorLocale;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PasswordUnlockController {

  private final PasswordUnlockUseCase unlockUseCase;
  private final TurnstileProperties turnstile;
  private final LinkHtmlRenderer html;
  private final VisitResponses responses;

  @OpenToSuspended
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
    Locale locale = VisitorLocale.resolve(acceptLanguage);
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
        return html.passwordPromptResponse(locale, status, shortCode, failed, turnstile.siteKey());
      }
      RedirectOutcome result = ((PasswordUnlockResult.Completed) unlocked).redirect();
      ResponseEntity<?> response = responses.unlocked(result, userAgent, locale);
      outcome =
          switch (result) {
            case RedirectOutcome.CountryBlocked b -> "blocked";
            case RedirectOutcome.DomainBlocked db -> "blocked";
            case RedirectOutcome.ExpiredWithMessage em -> "expired";
            case RedirectOutcome.NotYetOpen n -> "not_open";
            case RedirectOutcome.PrefetchDeclined pd -> "preview";
            default -> "redirect";
          };
      return response;
    } catch (LinkException e) {
      outcome = VisitResponses.outcomeOf(e);
      return responses.visitorErrorOrRethrow(e, locale);
    } finally {
      req.setAttribute(OutcomeResolver.ATTRIBUTE, outcome);
    }
  }
}
