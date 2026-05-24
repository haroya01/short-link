package com.example.short_link.link.presentation;

import com.example.short_link.common.observability.OutcomeResolver;
import com.example.short_link.link.application.ClickRecorder;
import com.example.short_link.link.application.CustomDomainService;
import com.example.short_link.link.application.GeoIpResolver;
import com.example.short_link.link.application.LinkLookupService;
import com.example.short_link.link.application.LinkPreviewCrawlerDetector;
import com.example.short_link.link.application.LinkPreviewRenderer;
import com.example.short_link.link.application.ShortLinkUrlBuilder;
import com.example.short_link.link.application.UserAgentClassifier;
import com.example.short_link.link.application.dto.CachedLink;
import com.example.short_link.link.application.dto.UserAgentInfo;
import com.example.short_link.link.application.helper.LinkHtmlRenderer;
import com.example.short_link.link.application.helper.LinkRedirectSupport;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /{shortCode}} — the read-side redirect pipeline: lookup, crawler preview vs human
 * branch, custom-domain owner check, country block, view-limit, click record, 302. The POST unlock
 * flow for password-protected links is a separate controller ({@link PasswordUnlockController}) so
 * this class stays a single-responsibility entry point. HTML page rendering (expired / blocked /
 * password prompt) lives in {@link LinkHtmlRenderer}, and the IP / OS / outcome helpers live in
 * {@link LinkRedirectSupport}.
 */
@RestController
@RequiredArgsConstructor
public class RedirectController {

  private final LinkLookupService lookup;
  private final ClickRecorder clickRecorder;
  private final LinkPreviewCrawlerDetector crawlerDetector;
  private final LinkPreviewRenderer previewRenderer;
  private final ShortLinkUrlBuilder urlBuilder;
  private final MeterRegistry meterRegistry;
  private final GeoIpResolver geoIpResolver;
  private final CustomDomainService customDomainService;
  private final UserAgentClassifier userAgentClassifier;

  @GetMapping("/{shortCode:[0-9A-Za-z]{3,16}}")
  public ResponseEntity<?> redirect(
      @PathVariable String shortCode,
      @RequestParam(value = "src", required = false) String src,
      @RequestHeader(value = "Referer", required = false) String referrer,
      @RequestHeader(value = "User-Agent", required = false) String userAgent,
      @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage,
      HttpServletRequest req) {
    Timer.Sample sample = Timer.start(meterRegistry);
    String outcome = "error";
    try {
      ResponseEntity<?> response =
          handleRedirect(shortCode, src, referrer, userAgent, acceptLanguage, req);
      outcome = LinkRedirectSupport.classifyOutcome(response);
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
      sample.stop(meterRegistry.timer("redirect.latency", "outcome", outcome));
      // Surface the domain outcome to the RequestMetricsFilter — expired / view_limit aren't
      // derivable from the HTTP status alone, so the filter would otherwise stamp the row with
      // the coarse status-based label and the link-metrics outcome breakdown would lose detail.
      req.setAttribute(OutcomeResolver.ATTRIBUTE, outcome);
    }
  }

  private ResponseEntity<?> handleRedirect(
      String shortCode,
      String src,
      String referrer,
      String userAgent,
      String acceptLanguage,
      HttpServletRequest req) {
    CachedLink link;
    try {
      link = lookup.findActiveLink(shortCode);
    } catch (LinkException e) {
      LinkEntity expired = lookup.findEntity(shortCode).orElse(null);
      if (expired != null && expired.getExpiredMessage() != null) {
        return LinkHtmlRenderer.expiredPageResponse(expired.getExpiredMessage());
      }
      throw e;
    }
    // If the request came in on a custom domain (e.g. go.brand.com), make sure the link belongs
    // to that domain's owner — otherwise we'd be exposing every kurl.me short code on every
    // customer's domain. Default Host (kurl.me, www.kurl.me) skips the check.
    Long customOwner = customDomainService.resolveOwner(req.getHeader("Host"));
    if (customOwner != null && !customOwner.equals(link.userId())) {
      throw new LinkException(LinkErrorCode.LINK_NOT_FOUND, shortCode);
    }
    String crawlerLabel = crawlerDetector.crawlerName(userAgent);
    if (crawlerLabel != null) {
      return handlePreview(
          shortCode, link, referrer, userAgent, acceptLanguage, src, crawlerLabel, req);
    }
    LinkEntity entity = lookup.findEntity(shortCode).orElse(null);
    if (entity != null && entity.hasPassword()) {
      return LinkHtmlRenderer.passwordPromptResponse(HttpStatus.OK, shortCode, false);
    }
    if (entity != null) {
      try {
        enforceViewLimit(entity);
      } catch (LinkException e) {
        if (entity.getExpiredMessage() != null) {
          return LinkHtmlRenderer.expiredPageResponse(entity.getExpiredMessage());
        }
        throw e;
      }
    }
    String clientCountry = geoIpResolver.resolve(LinkRedirectSupport.clientIp(req)).countryCode();
    if (link.isBlockedFor(clientCountry)) {
      meterRegistry
          .counter("redirect.blocked", "country", clientCountry == null ? "unknown" : clientCountry)
          .increment();
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
    return ResponseEntity.status(HttpStatus.FOUND)
        .location(URI.create(picked.url()))
        .header(HttpHeaders.CACHE_CONTROL, "private, max-age=90")
        .header("X-Robots-Tag", "noindex, nofollow")
        .build();
  }

  private ResponseEntity<?> handlePreview(
      String shortCode,
      CachedLink link,
      String referrer,
      String userAgent,
      String acceptLanguage,
      String src,
      String crawlerLabel,
      HttpServletRequest req) {
    meterRegistry.counter("short_link.preview").increment();
    // Persist the preview hit with bot=true + bot_name="preview:slackbot" etc. so per-link stats
    // can split social/messenger previews out of real clicks regardless of yauaa coverage.
    clickRecorder.recordPreview(
        link.linkId(),
        link.originalUrl(),
        referrer,
        userAgent,
        LinkRedirectSupport.clientIp(req),
        acceptLanguage,
        src,
        crawlerLabel);
    LinkEntity entity = lookup.findEntity(shortCode).orElse(null);
    if (entity == null) {
      return ResponseEntity.status(HttpStatus.FOUND)
          .location(URI.create(link.originalUrl()))
          .header("X-Robots-Tag", "noindex, nofollow")
          .build();
    }
    long clicks = lookup.countHumanClicks(link.linkId());
    String html = previewRenderer.render(entity, urlBuilder.build(shortCode), clicks);
    byte[] body = html.getBytes(StandardCharsets.UTF_8);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/html; charset=utf-8"))
        .contentLength(body.length)
        .header(HttpHeaders.CACHE_CONTROL, "public, max-age=300")
        .header("X-Robots-Tag", "noindex, nofollow")
        .body(body);
  }

  private void enforceViewLimit(LinkEntity entity) {
    if (entity.getMaxViews() == null) return;
    int updated = lookup.incrementViewCountIfBelowLimit(entity.getId());
    if (updated == 0) {
      throw new LinkException(LinkErrorCode.LINK_VIEW_LIMIT_EXCEEDED, entity.getShortCode());
    }
  }
}
