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
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LinkRedirectFlow {

  private final IncrementViewCountUseCase incrementViewCount;
  private final ClickRecorder clickRecorder;
  private final GeoIpResolver geoIpResolver;
  private final UserAgentClassifier userAgentClassifier;
  private final MeterRegistry meterRegistry;
  private final BlockedDomainChecker blockedDomainChecker;

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
    // 원본 URL이 아니라 변형 선택 후 실제 이동할 URL을 차단 검사한다.
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
    // 브라우저 프리페치는 미리보기로 집계해 사람 클릭에서 제외한다.
    if (visit.prefetch()) {
      clickRecorder.recordPreview(ctx, "prefetch");
    } else {
      clickRecorder.record(ctx);
    }
    return new RedirectOutcome.Redirect(picked);
  }

  private static String normalizeOs(String osName) {
    if (osName == null) return null;
    String lower = osName.toLowerCase(Locale.ROOT);
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
