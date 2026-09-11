package com.example.short_link.link.redirect.application;

import com.example.short_link.common.security.BlockedDomainChecker;
import com.example.short_link.link.application.dto.CachedLink;
import com.example.short_link.link.application.dto.UserAgentInfo;
import com.example.short_link.link.application.write.IncrementViewCountCommand;
import com.example.short_link.link.application.write.IncrementViewCountUseCase;
import com.example.short_link.link.classifier.application.GeoIpResolver;
import com.example.short_link.link.classifier.application.UserAgentClassifier;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
import com.example.short_link.link.stats.application.ClickContext;
import com.example.short_link.link.stats.application.ClickRecorder;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Shared decision pipeline behind {@code GET /{shortCode}} and {@code POST /{shortCode}} (password
 * unlock). Controllers hand off the loaded cache DTO, plus the entity only when the password-unlock
 * path already needed it. The flow runs the checks that don't depend on the entry point — view
 * limit, country block, destination pick, operator domain blocklist, click recording — returning a
 * {@link RedirectOutcome} for the presentation layer to render.
 */
@Service
@RequiredArgsConstructor
public class LinkRedirectFlow {

  private final IncrementViewCountUseCase incrementViewCount;
  private final ClickRecorder clickRecorder;
  private final GeoIpResolver geoIpResolver;
  private final UserAgentClassifier userAgentClassifier;
  private final MeterRegistry meterRegistry;
  private final BlockedDomainChecker blockedDomainChecker;

  /**
   * Run the post-load redirect pipeline: view-limit, country block, destination pick, click record.
   * Throws {@link LinkException} for view-limit-exceeded; returns {@link RedirectOutcome.Blocked} /
   * {@link RedirectOutcome.ExpiredWithMessage} for the interstitial-rendering branches; otherwise
   * {@link RedirectOutcome.Redirect}.
   */
  public RedirectOutcome execute(CachedLink link, LinkEntity entity, RedirectVisit visit) {
    try {
      enforceViewLimit(link, entity);
    } catch (LinkException e) {
      String expiredMessage = entity == null ? link.expiredMessage() : entity.getExpiredMessage();
      if (e.errorCode() == LinkErrorCode.LINK_VIEW_LIMIT_EXCEEDED && expiredMessage != null) {
        return new RedirectOutcome.ExpiredWithMessage(expiredMessage);
      }
      throw e;
    }
    String clientCountry = geoIpResolver.resolve(visit.clientIp()).countryCode();
    if (link.isBlockedFor(clientCountry)) {
      meterRegistry
          .counter("redirect.blocked", "country", clientCountry == null ? "unknown" : clientCountry)
          .increment();
      return new RedirectOutcome.Blocked();
    }
    UserAgentInfo ua = userAgentClassifier.classify(visit.userAgent());
    CachedLink.Picked picked = link.pick(clientCountry, normalizeOs(ua.osName()), ua.deviceClass());
    // 목적지 변형(variant)까지 포함해 실제 302 대상 URL 기준으로 검사 — 생성 후 차단된 도메인도 여기서 죽는다.
    if (blockedDomainChecker.isBlocked(picked.url())) {
      meterRegistry.counter("redirect.domain_blocked").increment();
      return new RedirectOutcome.DomainBlocked();
    }
    ClickContext ctx =
        ClickContext.of(
                link.linkId(),
                picked.url(),
                visit.referrer(),
                visit.userAgent(),
                visit.clientIp(),
                visit.acceptLanguage())
            .withSourceChannel(visit.sourceChannel())
            .withDestination(picked.destinationId())
            .withPostId(visit.postId())
            .withGpc(visit.gpc())
            .withFetchSite(visit.fetchSite());
    // 브라우저 프리페치는 사람이 연 게 아니다 — Sec-Fetch(Sec-Purpose/Purpose/X-moz)로 가려 프리뷰로 집계해
    // 사람 클릭에서 뺀다(정직성). 진짜 클릭만 humanClicks 로 남는다.
    if (visit.prefetch()) {
      clickRecorder.recordPreview(ctx, "prefetch");
    } else {
      clickRecorder.record(ctx);
    }
    return new RedirectOutcome.Redirect(picked);
  }

  private static String normalizeOs(String osName) {
    if (osName == null) return null;
    String lower = osName.toLowerCase();
    if (lower.contains("android")) return "android";
    if (lower.contains("ios")) return "ios";
    if (lower.contains("mac")) return "macos";
    if (lower.contains("windows")) return "windows";
    if (lower.contains("linux")) return "linux";
    return null;
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
