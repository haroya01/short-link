package com.example.short_link.link.redirect.application;

import com.example.short_link.link.application.dto.CachedLink;
import com.example.short_link.link.application.dto.UserAgentInfo;
import com.example.short_link.link.application.read.LinkLookupQueryService;
import com.example.short_link.link.application.write.IncrementViewCountCommand;
import com.example.short_link.link.application.write.IncrementViewCountUseCase;
import com.example.short_link.link.classifier.application.GeoIpResolver;
import com.example.short_link.link.classifier.application.UserAgentClassifier;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
import com.example.short_link.link.redirect.application.helper.LinkRedirectSupport;
import com.example.short_link.link.stats.application.ClickRecorder;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Shared decision pipeline behind {@code GET /{shortCode}} and {@code POST /{shortCode}} (password
 * unlock). Both controllers hand off the loaded link + the (already-password-checked) entity here,
 * and the flow runs the four checks that don't depend on the entry point — view limit, country
 * block, destination pick, click recording — returning a {@link RedirectOutcome} for the
 * presentation layer to render. Lifts the duplicated check chain out of the two controllers and
 * keeps the entry-point-specific bits (preview detection / password prompt) where they belong.
 */
@Service
@RequiredArgsConstructor
public class LinkRedirectFlow {

  private final LinkLookupQueryService lookup;
  private final IncrementViewCountUseCase incrementViewCount;
  private final ClickRecorder clickRecorder;
  private final GeoIpResolver geoIpResolver;
  private final UserAgentClassifier userAgentClassifier;
  private final MeterRegistry meterRegistry;

  /**
   * Run the post-load redirect pipeline: view-limit, country block, destination pick, click record.
   * Throws {@link LinkException} for view-limit-exceeded; returns {@link RedirectOutcome.Blocked} /
   * {@link RedirectOutcome.ExpiredWithMessage} for the interstitial-rendering branches; otherwise
   * {@link RedirectOutcome.Redirect}.
   */
  public RedirectOutcome execute(
      CachedLink link,
      LinkEntity entity,
      String referrer,
      String userAgent,
      String acceptLanguage,
      String src,
      HttpServletRequest req) {
    if (entity != null) {
      try {
        enforceViewLimit(entity);
      } catch (LinkException e) {
        if (e.errorCode() == LinkErrorCode.LINK_VIEW_LIMIT_EXCEEDED
            && entity.getExpiredMessage() != null) {
          return new RedirectOutcome.ExpiredWithMessage(entity.getExpiredMessage());
        }
        throw e;
      }
    }
    String clientCountry = geoIpResolver.resolve(LinkRedirectSupport.clientIp(req)).countryCode();
    if (link.isBlockedFor(clientCountry)) {
      meterRegistry
          .counter("redirect.blocked", "country", clientCountry == null ? "unknown" : clientCountry)
          .increment();
      return new RedirectOutcome.Blocked();
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
    return new RedirectOutcome.Redirect(picked);
  }

  private void enforceViewLimit(LinkEntity entity) {
    if (entity.getMaxViews() == null) return;
    int updated = incrementViewCount.execute(new IncrementViewCountCommand(entity.getId()));
    if (updated == 0) {
      throw new LinkException(LinkErrorCode.LINK_VIEW_LIMIT_EXCEEDED, entity.getShortCode());
    }
  }
}
