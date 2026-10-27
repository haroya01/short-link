package com.example.short_link.link.redirect.application;

import com.example.short_link.link.application.dto.CachedLink;
import com.example.short_link.link.domain.LinkEntity;
import java.time.Instant;

public final class RedirectDecider {

  public interface Facts {
    Long hostOwner();

    boolean blocked(String url);

    String crawlerName();

    Instant now();
  }

  private RedirectDecider() {}

  public static RedirectDecision.Live decide(CachedLink link, Facts facts) {
    // Match the inbound Host owner before exposing a link on a custom domain.
    Long hostOwner = facts.hostOwner();
    if (hostOwner != null && !hostOwner.equals(link.userId())) {
      return new RedirectDecision.NotOnThisHost();
    }
    // 크롤러는 지오·AB 선택 맥락이 없으므로 활성 목적지 하나라도 차단되면 미리보기도 막는다.
    if (anyDestinationBlocked(link, facts)) {
      return new RedirectDecision.DestinationBlocked();
    }
    // 공개 전에는 비밀번호도 묻지 않고, 미리보기 카드에도 목적지를 싣지 않는다.
    if (link.visitOptions().opensLaterThan(facts.now())) {
      String crawler = facts.crawlerName();
      if (crawler == null) {
        return new RedirectDecision.NotYetOpen(link.visitOptions().opensAt());
      }
      return new RedirectDecision.Preview(
          link.passwordRequired() || link.limitsVisitors()
              ? PreviewScope.KURL_CARD_ONLY
              : PreviewScope.WITHOUT_DESTINATION_URL,
          crawler);
    }
    // 비밀번호 검사는 크롤러 분기보다 먼저 해야 스푸핑된 UA가 목적지를 노출하지 못한다.
    if (link.passwordRequired()) {
      return new RedirectDecision.PasswordPrompt();
    }
    String crawler = facts.crawlerName();
    if (crawler == null) {
      return new RedirectDecision.Visit();
    }
    return link.limitsVisitors()
        ? new RedirectDecision.LimitedCrawler(crawler)
        : new RedirectDecision.Preview(PreviewScope.DESTINATION, crawler);
  }

  public static RedirectDecision.Expired decideExpired(LinkEntity expired, Facts facts) {
    if (expired == null) {
      return new RedirectDecision.Unhandled();
    }
    if (expired.getExpiredRedirectUrl() != null) {
      Long hostOwner = facts.hostOwner();
      if (hostOwner != null && !hostOwner.equals(expired.getUserId())) {
        return new RedirectDecision.NotOnThisHost();
      }
      if (facts.blocked(expired.getExpiredRedirectUrl())) {
        return new RedirectDecision.DestinationBlocked();
      }
      return new RedirectDecision.AfterExpiry(expired.getExpiredRedirectUrl());
    }
    if (expired.getExpiredMessage() != null) {
      return new RedirectDecision.ExpiredMessage(expired.getExpiredMessage());
    }
    return new RedirectDecision.Unhandled();
  }

  private static boolean anyDestinationBlocked(CachedLink link, Facts facts) {
    if (facts.blocked(link.originalUrl())) {
      return true;
    }
    for (CachedLink.Variant variant : link.variants()) {
      if (variant.enabled() && facts.blocked(variant.url())) {
        return true;
      }
    }
    return false;
  }
}
