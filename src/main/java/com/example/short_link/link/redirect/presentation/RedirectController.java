package com.example.short_link.link.redirect.presentation;

import com.example.short_link.common.observability.OutcomeResolver;
import com.example.short_link.common.security.BlockedDomainChecker;
import com.example.short_link.customdomain.application.read.CustomDomainQueryService;
import com.example.short_link.link.access.application.TurnstileProperties;
import com.example.short_link.link.application.ShortLinkUrlBuilder;
import com.example.short_link.link.application.dto.CachedLink;
import com.example.short_link.link.application.read.LinkLookupQueryService;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
import com.example.short_link.link.redirect.application.LinkPreviewCrawlerDetector;
import com.example.short_link.link.redirect.application.LinkRedirectFlow;
import com.example.short_link.link.redirect.application.PreviewScope;
import com.example.short_link.link.redirect.application.RedirectDecider;
import com.example.short_link.link.redirect.application.RedirectDecision;
import com.example.short_link.link.redirect.application.RedirectOutcome;
import com.example.short_link.link.redirect.presentation.helper.LinkHtmlRenderer;
import com.example.short_link.link.redirect.presentation.helper.LinkPreviewRenderer;
import com.example.short_link.link.redirect.presentation.helper.LinkRedirectSupport;
import com.example.short_link.link.redirect.presentation.helper.VisitHandoff;
import com.example.short_link.link.redirect.presentation.helper.VisitResponses;
import com.example.short_link.link.redirect.presentation.helper.VisitorLocale;
import com.example.short_link.link.stats.application.ClickContext;
import com.example.short_link.link.stats.application.ClickRecorder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
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
  private final TurnstileProperties turnstile;
  private final BlockedDomainChecker blockedDomainChecker;
  private final LinkHtmlRenderer html;
  private final VisitHandoff handoff;
  private final VisitResponses responses;
  private final Clock clock;

  @GetMapping({"/{shortCode:[0-9A-Za-z]{3,16}}", "/{shortCode:[0-9A-Za-z]{3,16}}/"})
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
    Locale locale = VisitorLocale.resolve(acceptLanguage);
    try {
      ResponseEntity<?> response =
          handleRedirect(
              new Visitor(shortCode, src, post, referrer, userAgent, acceptLanguage, locale, req));
      outcome = LinkRedirectSupport.classifyOutcome(response);
      return response;
    } catch (LinkException e) {
      outcome = VisitResponses.outcomeOf(e);
      return responses.visitorErrorOrRethrow(e, locale);
    } finally {
      sample.stop(meterRegistry.timer("redirect.latency", "outcome", outcome));
      req.setAttribute(OutcomeResolver.ATTRIBUTE, outcome);
    }
  }

  private record Visitor(
      ShortCode shortCode,
      String src,
      Long post,
      String referrer,
      String userAgent,
      String acceptLanguage,
      Locale locale,
      HttpServletRequest req) {}

  private ResponseEntity<?> handleRedirect(Visitor visitor) {
    RedirectDecider.Facts facts = facts(visitor);
    CachedLink link;
    try {
      link = lookup.findActiveLink(visitor.shortCode());
    } catch (LinkException e) {
      if (e.errorCode() != LinkErrorCode.LINK_EXPIRED) {
        throw e;
      }
      LinkEntity expired = lookup.findEntity(visitor.shortCode()).orElse(null);
      return switch (RedirectDecider.decideExpired(expired, facts)) {
        case RedirectDecision.NotOnThisHost n ->
            throw new LinkException(LinkErrorCode.LINK_NOT_FOUND, expired.getShortCode());
        case RedirectDecision.DestinationBlocked d -> domainBlocked(visitor.locale());
        case RedirectDecision.AfterExpiry a -> handoff.afterExpiry(a.url());
        case RedirectDecision.ExpiredMessage m ->
            html.expiredPageResponse(visitor.locale(), m.message());
        case RedirectDecision.Unhandled u -> throw e;
      };
    }
    return switch (RedirectDecider.decide(link, facts)) {
      case RedirectDecision.NotOnThisHost n ->
          throw new LinkException(LinkErrorCode.LINK_NOT_FOUND, visitor.shortCode());
      case RedirectDecision.DestinationBlocked d -> domainBlocked(visitor.locale());
      case RedirectDecision.Preview p ->
          preview(
              visitor,
              link,
              lookup.findEntity(visitor.shortCode()).orElse(null),
              p.crawler(),
              p.scope());
      case RedirectDecision.NotYetOpen n ->
          html.notYetOpenPageResponse(visitor.locale(), n.opensAt());
      case RedirectDecision.PasswordPrompt p ->
          html.passwordPromptResponse(
              visitor.locale(), HttpStatus.OK, visitor.shortCode(), false, turnstile.siteKey());
      case RedirectDecision.LimitedCrawler c -> limitedCrawler(visitor, link, c.crawler());
      case RedirectDecision.Visit v ->
          responses.redirect(
              flow.execute(
                  link,
                  null,
                  LinkRedirectSupport.visit(
                      visitor.referrer(),
                      visitor.userAgent(),
                      visitor.acceptLanguage(),
                      visitor.src(),
                      visitor.post(),
                      visitor.req())),
              visitor.userAgent(),
              visitor.locale());
    };
  }

  private RedirectDecider.Facts facts(Visitor visitor) {
    return new RedirectDecider.Facts() {
      @Override
      public Long hostOwner() {
        return customDomainService.resolveOwner(visitor.req().getHeader("Host"));
      }

      @Override
      public boolean blocked(String url) {
        return blockedDomainChecker.isBlocked(url);
      }

      @Override
      public String crawlerName() {
        return crawlerDetector.crawlerName(visitor.userAgent());
      }

      @Override
      public Instant now() {
        return clock.instant();
      }
    };
  }

  private ResponseEntity<?> domainBlocked(Locale locale) {
    meterRegistry.counter("redirect.domain_blocked").increment();
    return html.domainBlockedPageResponse(locale);
  }

  private ResponseEntity<?> limitedCrawler(Visitor visitor, CachedLink link, String crawler) {
    LinkEntity entity = lookup.findEntity(visitor.shortCode()).orElse(null);
    Optional<RedirectOutcome> refused =
        flow.refuseCrawler(link, entity, LinkRedirectSupport.clientIp(visitor.req()));
    if (refused.isPresent()) {
      return responses.redirect(refused.get(), visitor.userAgent(), visitor.locale());
    }
    return preview(visitor, link, entity, crawler, PreviewScope.KURL_CARD_ONLY);
  }

  private ResponseEntity<?> preview(
      Visitor visitor,
      CachedLink link,
      LinkEntity entity,
      String crawlerLabel,
      PreviewScope scope) {
    meterRegistry.counter("short_link.preview").increment();
    clickRecorder.recordPreview(
        ClickContext.of(
                link.linkId(),
                link.originalUrl(),
                visitor.referrer(),
                visitor.userAgent(),
                LinkRedirectSupport.clientIp(visitor.req()),
                visitor.acceptLanguage())
            .withSourceChannel(visitor.src())
            .withFetchSite(LinkRedirectSupport.fetchSite(visitor.req())),
        crawlerLabel);
    boolean revealDestination = scope == PreviewScope.DESTINATION;
    if (entity == null && !revealDestination) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }
    if (entity == null) {
      return ResponseEntity.status(HttpStatus.FOUND)
          .location(URI.create(link.originalUrl()))
          .header("X-Robots-Tag", "noindex, nofollow")
          .build();
    }
    long clicks = lookup.countHumanClicks(link.linkId());
    String html =
        previewRenderer.render(entity, urlBuilder.build(visitor.shortCode()), clicks, scope);
    byte[] body = html.getBytes(StandardCharsets.UTF_8);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/html; charset=utf-8"))
        .contentLength(body.length)
        .header(HttpHeaders.CACHE_CONTROL, "public, max-age=300")
        .header("X-Robots-Tag", "noindex, nofollow")
        .body(body);
  }
}
