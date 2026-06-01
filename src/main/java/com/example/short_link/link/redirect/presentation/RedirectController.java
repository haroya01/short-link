package com.example.short_link.link.redirect.presentation;

import com.example.short_link.common.observability.OutcomeResolver;
import com.example.short_link.customdomain.application.read.CustomDomainQueryService;
import com.example.short_link.link.application.ShortLinkUrlBuilder;
import com.example.short_link.link.application.dto.CachedLink;
import com.example.short_link.link.application.read.LinkLookupQueryService;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
import com.example.short_link.link.redirect.application.LinkPreviewCrawlerDetector;
import com.example.short_link.link.redirect.application.LinkPreviewRenderer;
import com.example.short_link.link.redirect.application.LinkRedirectFlow;
import com.example.short_link.link.redirect.application.RedirectOutcome;
import com.example.short_link.link.redirect.application.helper.LinkHtmlRenderer;
import com.example.short_link.link.redirect.application.helper.LinkRedirectSupport;
import com.example.short_link.link.stats.application.ClickContext;
import com.example.short_link.link.stats.application.ClickRecorder;
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
 * {@code GET /{shortCode}} entry point. Handles the entry-point-specific bits — preview-crawler
 * branch, custom-domain owner check, password-protected detection — and delegates the post-load
 * check chain to {@link LinkRedirectFlow}. {@link RedirectOutcome} pattern-match then renders the
 * appropriate HTTP shape.
 */
@RestController
@RequiredArgsConstructor
public class RedirectController {

  private final LinkLookupQueryService lookup;
  private final LinkRedirectFlow flow;
  private final ClickRecorder clickRecorder;
  private final LinkPreviewCrawlerDetector crawlerDetector;
  private final LinkPreviewRenderer previewRenderer;
  private final ShortLinkUrlBuilder urlBuilder;
  private final MeterRegistry meterRegistry;
  private final CustomDomainQueryService customDomainService;

  @GetMapping("/{shortCode:[0-9A-Za-z]{3,16}}")
  public ResponseEntity<?> redirect(
      @PathVariable ShortCode shortCode,
      @RequestParam(value = "src", required = false) String src,
      @RequestParam(value = "post", required = false) Long post,
      @RequestHeader(value = "Referer", required = false) String referrer,
      @RequestHeader(value = "User-Agent", required = false) String userAgent,
      @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage,
      HttpServletRequest req) {
    Timer.Sample sample = Timer.start(meterRegistry);
    String outcome = "error";
    try {
      ResponseEntity<?> response =
          handleRedirect(shortCode, src, post, referrer, userAgent, acceptLanguage, req);
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
      req.setAttribute(OutcomeResolver.ATTRIBUTE, outcome);
    }
  }

  private ResponseEntity<?> handleRedirect(
      ShortCode shortCode,
      String src,
      Long post,
      String referrer,
      String userAgent,
      String acceptLanguage,
      HttpServletRequest req) {
    CachedLink link;
    try {
      link = lookup.findActiveLink(shortCode);
    } catch (LinkException e) {
      if (e.errorCode() == LinkErrorCode.LINK_EXPIRED) {
        LinkEntity expired = lookup.findEntity(shortCode).orElse(null);
        if (expired != null && expired.getExpiredMessage() != null) {
          return LinkHtmlRenderer.expiredPageResponse(expired.getExpiredMessage());
        }
      }
      throw e;
    }
    // Custom-domain owner check — keep here, not in the flow, because it's a pre-flight check that
    // depends on the inbound Host header, not on the post-load decision chain.
    Long customOwner = customDomainService.resolveOwner(req.getHeader("Host"));
    if (customOwner != null && !customOwner.equals(link.userId())) {
      throw new LinkException(LinkErrorCode.LINK_NOT_FOUND, shortCode);
    }
    String crawlerLabel = crawlerDetector.crawlerName(userAgent);
    if (crawlerLabel != null) {
      return handlePreview(
          shortCode, link, referrer, userAgent, acceptLanguage, src, crawlerLabel, req);
    }
    if (link.passwordRequired()) {
      return LinkHtmlRenderer.passwordPromptResponse(HttpStatus.OK, shortCode, false);
    }
    return render(flow.execute(link, null, referrer, userAgent, acceptLanguage, src, post, req));
  }

  private ResponseEntity<?> render(RedirectOutcome outcome) {
    return switch (outcome) {
      case RedirectOutcome.Redirect r ->
          ResponseEntity.status(HttpStatus.FOUND)
              .location(URI.create(r.picked().url()))
              .header(HttpHeaders.CACHE_CONTROL, "private, max-age=90")
              .header("X-Robots-Tag", "noindex, nofollow")
              .build();
      case RedirectOutcome.Blocked b -> LinkHtmlRenderer.blockedPageResponse();
      case RedirectOutcome.ExpiredWithMessage em ->
          LinkHtmlRenderer.expiredPageResponse(em.message());
      case RedirectOutcome.PasswordRequired pr ->
          throw new IllegalStateException(
              "PasswordRequired decided at controller before flow.execute()");
    };
  }

  private ResponseEntity<?> handlePreview(
      ShortCode shortCode,
      CachedLink link,
      String referrer,
      String userAgent,
      String acceptLanguage,
      String src,
      String crawlerLabel,
      HttpServletRequest req) {
    meterRegistry.counter("short_link.preview").increment();
    clickRecorder.recordPreview(
        ClickContext.of(
                link.linkId(),
                link.originalUrl(),
                referrer,
                userAgent,
                LinkRedirectSupport.clientIp(req),
                acceptLanguage)
            .withSourceChannel(src),
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
}
