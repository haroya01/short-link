package com.example.short_link.link.presentation;

import com.example.short_link.common.observability.OutcomeResolver;
import com.example.short_link.link.application.ClickRecorder;
import com.example.short_link.link.application.GeoIpResolver;
import com.example.short_link.link.application.LinkLookupService;
import com.example.short_link.link.application.LinkProtectionService;
import com.example.short_link.link.application.UserAgentClassifier;
import com.example.short_link.link.application.dto.CachedLink;
import com.example.short_link.link.application.dto.UserAgentInfo;
import com.example.short_link.link.application.helper.LinkHtmlRenderer;
import com.example.short_link.link.application.helper.LinkRedirectSupport;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.exception.LinkExpiredException;
import com.example.short_link.link.exception.LinkNotFoundException;
import com.example.short_link.link.exception.LinkViewLimitExceededException;
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
 * Password unlock for protected links. Lives in its own controller — same path as {@link
 * RedirectController} but a separate write-shaped flow (form-encoded POST, password check, then
 * either reject with the prompt or fall through into the normal redirect pipeline). Splitting lets
 * {@code RedirectController} focus on the read-side redirect path without a second method carrying
 * its own copy of password / view-limit / country / UA / click branches.
 */
@RestController
@RequiredArgsConstructor
public class PasswordUnlockController {

  private final LinkLookupService lookup;
  private final LinkProtectionService protectionService;
  private final ClickRecorder clickRecorder;
  private final GeoIpResolver geoIpResolver;
  private final UserAgentClassifier userAgentClassifier;

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
          lookup.findEntity(shortCode).orElseThrow(() -> new LinkNotFoundException(shortCode));
      if (entity.hasPassword() && !protectionService.checkPassword(entity, password)) {
        outcome = "password_required";
        return LinkHtmlRenderer.passwordPromptResponse(HttpStatus.UNAUTHORIZED, shortCode, true);
      }
      try {
        enforceViewLimit(entity);
      } catch (LinkViewLimitExceededException e) {
        outcome = "view_limit";
        throw e;
      }
      String clientCountry = geoIpResolver.resolve(LinkRedirectSupport.clientIp(req)).countryCode();
      if (link.isBlockedFor(clientCountry)) {
        outcome = "blocked";
        return LinkHtmlRenderer.blockedPageResponse();
      }
      UserAgentInfo ua = userAgentClassifier.classify(userAgent);
      CachedLink.Picked picked =
          link.pick(clientCountry, LinkRedirectSupport.normalizeOs(ua.osName()), ua.deviceClass());
      clickRecorder.record(
          link.linkId(),
          picked.url(),
          referrer,
          userAgent,
          LinkRedirectSupport.clientIp(req),
          acceptLanguage,
          src,
          picked.destinationId());
      outcome = "redirect";
      return ResponseEntity.status(HttpStatus.FOUND)
          .location(URI.create(picked.url()))
          .header(HttpHeaders.CACHE_CONTROL, "no-store")
          .header("X-Robots-Tag", "noindex, nofollow")
          .build();
    } catch (LinkNotFoundException e) {
      outcome = "not_found";
      throw e;
    } catch (LinkExpiredException e) {
      outcome = "expired";
      throw e;
    } finally {
      req.setAttribute(OutcomeResolver.ATTRIBUTE, outcome);
    }
  }

  private void enforceViewLimit(LinkEntity entity) {
    if (entity.getMaxViews() == null) return;
    int updated = lookup.incrementViewCountIfBelowLimit(entity.getId());
    if (updated == 0) {
      throw new LinkViewLimitExceededException(entity.getShortCode());
    }
  }
}
