package com.example.short_link.link.access.presentation;

import com.example.short_link.common.observability.OutcomeResolver;
import com.example.short_link.link.access.application.LinkProtectionService;
import com.example.short_link.link.application.LinkLookupService;
import com.example.short_link.link.application.dto.CachedLink;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
import com.example.short_link.link.redirect.application.LinkRedirectFlow;
import com.example.short_link.link.redirect.application.RedirectOutcome;
import com.example.short_link.link.redirect.application.helper.LinkHtmlRenderer;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
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

  private final LinkLookupService lookup;
  private final LinkProtectionService protectionService;
  private final LinkRedirectFlow flow;

  @PostMapping(
      value = "/{shortCode:[0-9A-Za-z]{3,16}}",
      consumes = "application/x-www-form-urlencoded")
  public ResponseEntity<?> unlock(
      @PathVariable String shortCode,
      @RequestParam("password") String password,
      @RequestParam(value = "src", required = false) String src,
      @RequestHeader(value = "Referer", required = false) String referrer,
      @RequestHeader(value = "User-Agent", required = false) String userAgent,
      @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage,
      HttpServletRequest req) {
    String outcome = "error";
    try {
      CachedLink link = lookup.findActiveLink(shortCode);
      LinkEntity entity =
          lookup
              .findEntity(shortCode)
              .orElseThrow(() -> new LinkException(LinkErrorCode.LINK_NOT_FOUND, shortCode));
      if (entity.hasPassword() && !protectionService.checkPassword(entity, password)) {
        outcome = "password_required";
        return LinkHtmlRenderer.passwordPromptResponse(HttpStatus.UNAUTHORIZED, shortCode, true);
      }
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
      throw e;
    } finally {
      req.setAttribute(OutcomeResolver.ATTRIBUTE, outcome);
    }
  }

  private ResponseEntity<?> renderUnlock(RedirectOutcome outcome) {
    return switch (outcome) {
      case RedirectOutcome.Redirect r ->
          ResponseEntity.status(HttpStatus.FOUND)
              .location(URI.create(r.picked().url()))
              .header(HttpHeaders.CACHE_CONTROL, "no-store")
              .header("X-Robots-Tag", "noindex, nofollow")
              .build();
      case RedirectOutcome.Blocked b -> LinkHtmlRenderer.blockedPageResponse();
      case RedirectOutcome.ExpiredWithMessage em ->
          LinkHtmlRenderer.expiredPageResponse(em.message());
      case RedirectOutcome.PasswordRequired pr ->
          throw new IllegalStateException("PasswordRequired not reachable from unlock flow");
    };
  }
}
