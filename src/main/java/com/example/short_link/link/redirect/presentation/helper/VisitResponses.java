package com.example.short_link.link.redirect.presentation.helper;

import com.example.short_link.link.exception.LinkException;
import com.example.short_link.link.redirect.application.RedirectOutcome;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VisitResponses {

  private final LinkHtmlRenderer html;
  private final VisitHandoff handoff;

  public ResponseEntity<?> redirect(RedirectOutcome outcome, String userAgent, Locale locale) {
    if (outcome instanceof RedirectOutcome.Redirect r) {
      return handoff.redirect(r, userAgent, locale);
    }
    return page(outcome, locale);
  }

  public ResponseEntity<?> unlocked(RedirectOutcome outcome, String userAgent, Locale locale) {
    if (outcome instanceof RedirectOutcome.Redirect r) {
      return handoff.unlocked(r, userAgent, locale);
    }
    return page(outcome, locale);
  }

  public static String outcomeOf(LinkException e) {
    return switch (e.errorCode()) {
      case LINK_NOT_FOUND -> "not_found";
      case LINK_EXPIRED -> "expired";
      case LINK_VIEW_LIMIT_EXCEEDED -> "view_limit";
      case LINK_DISABLED -> "disabled";
      default -> "error";
    };
  }

  // 방문자가 연 링크 — 만료·한도초과·없음은 JSON 대신 브랜드 HTML 페이지로 보여준다.
  public ResponseEntity<byte[]> visitorErrorOrRethrow(LinkException e, Locale locale) {
    return html.visitorErrorPage(locale, e.errorCode()).orElseThrow(() -> e);
  }

  private ResponseEntity<?> page(RedirectOutcome outcome, Locale locale) {
    return switch (outcome) {
      case RedirectOutcome.CountryBlocked b -> html.blockedPageResponse(locale);
      case RedirectOutcome.DomainBlocked db -> html.domainBlockedPageResponse(locale);
      case RedirectOutcome.ExpiredWithMessage em -> html.expiredPageResponse(locale, em.message());
      case RedirectOutcome.NotYetOpen n -> html.notYetOpenPageResponse(locale, n.opensAt());
      case RedirectOutcome.PrefetchDeclined pd -> handoff.prefetchDeclined();
      case RedirectOutcome.PasswordRequired pr ->
          throw new IllegalStateException("PasswordRequired is decided before the redirect flow");
      case RedirectOutcome.Redirect r -> throw new IllegalStateException("handled by the caller");
    };
  }
}
