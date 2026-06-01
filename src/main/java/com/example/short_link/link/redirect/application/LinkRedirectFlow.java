package com.example.short_link.link.redirect.application;

import com.example.short_link.link.application.dto.CachedLink;
import com.example.short_link.link.application.dto.UserAgentInfo;
import com.example.short_link.link.application.write.IncrementViewCountCommand;
import com.example.short_link.link.application.write.IncrementViewCountUseCase;
import com.example.short_link.link.classifier.application.GeoIpResolver;
import com.example.short_link.link.classifier.application.UserAgentClassifier;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
import com.example.short_link.link.redirect.application.helper.LinkRedirectSupport;
import com.example.short_link.link.stats.application.ClickContext;
import com.example.short_link.link.stats.application.ClickRecorder;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Shared decision pipeline behind {@code GET /{shortCode}} and {@code POST /{shortCode}} (password
 * unlock). Controllers hand off the loaded cache DTO, plus the entity only when the password-unlock
 * path already needed it. The flow runs the four checks that don't depend on the entry point — view
 * limit, country block, destination pick, click recording — returning a {@link RedirectOutcome} for
 * the presentation layer to render.
 */
@Service
@RequiredArgsConstructor
public class LinkRedirectFlow {

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
    return execute(link, entity, referrer, userAgent, acceptLanguage, src, null, req);
  }

  /**
   * Overload that also attributes the click to a blog post (the redirect carried {@code ?post=}).
   */
  public RedirectOutcome execute(
      CachedLink link,
      LinkEntity entity,
      String referrer,
      String userAgent,
      String acceptLanguage,
      String src,
      Long postId,
      HttpServletRequest req) {
    if (entity != null) {
      try {
        enforceViewLimit(link, entity);
      } catch (LinkException e) {
        if (e.errorCode() == LinkErrorCode.LINK_VIEW_LIMIT_EXCEEDED
            && entity.getExpiredMessage() != null) {
          return new RedirectOutcome.ExpiredWithMessage(entity.getExpiredMessage());
        }
        throw e;
      }
    } else {
      try {
        enforceViewLimit(link, null);
      } catch (LinkException e) {
        if (e.errorCode() == LinkErrorCode.LINK_VIEW_LIMIT_EXCEEDED
            && link.expiredMessage() != null) {
          return new RedirectOutcome.ExpiredWithMessage(link.expiredMessage());
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
        ClickContext.of(
                link.linkId(),
                picked.url(),
                referrer,
                userAgent,
                LinkRedirectSupport.clientIp(req),
                acceptLanguage)
            .withSourceChannel(src)
            .withDestination(picked.destinationId())
            .withPostId(postId));
    return new RedirectOutcome.Redirect(picked);
  }

  private void enforceViewLimit(CachedLink link, LinkEntity entity) {
    Integer maxViews = entity == null ? link.maxViews() : entity.getMaxViews();
    if (maxViews == null) return;
    int updated = incrementViewCount.execute(new IncrementViewCountCommand(link.linkId()));
    if (updated == 0) {
      Object code =
          entity != null
              ? entity.getShortCode()
              : link.shortCode() == null ? link.linkId() : link.shortCode();
      throw new LinkException(LinkErrorCode.LINK_VIEW_LIMIT_EXCEEDED, code);
    }
  }
}
