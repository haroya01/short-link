package com.example.short_link.link.redirect.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.observability.OutcomeResolver;
import com.example.short_link.common.security.BlockedDomainChecker;
import com.example.short_link.customdomain.application.read.CustomDomainQueryService;
import com.example.short_link.link.access.application.TurnstileProperties;
import com.example.short_link.link.application.ShortLinkUrlBuilder;
import com.example.short_link.link.application.dto.CachedLink;
import com.example.short_link.link.application.dto.CachedLinkFixture;
import com.example.short_link.link.application.read.LinkLookupQueryService;
import com.example.short_link.link.classifier.application.ClientAppClassifier;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
import com.example.short_link.link.redirect.application.LinkPreviewCrawlerDetector;
import com.example.short_link.link.redirect.application.LinkRedirectFlow;
import com.example.short_link.link.redirect.application.PreviewScope;
import com.example.short_link.link.redirect.application.RedirectOutcome;
import com.example.short_link.link.redirect.presentation.helper.LinkHtmlRenderer;
import com.example.short_link.link.redirect.presentation.helper.LinkPreviewRenderer;
import com.example.short_link.link.redirect.presentation.helper.VisitHandoff;
import com.example.short_link.link.redirect.presentation.helper.VisitResponses;
import com.example.short_link.link.stats.application.ClickRecorder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

class RedirectControllerDecisionTest {

  private static final ShortCode CODE = new ShortCode("abc1234");
  private static final Instant NOW = Instant.parse("2026-10-27T12:00:00Z");
  private static final String BOT_UA = "Slackbot-LinkExpanding 1.0";

  private final LinkLookupQueryService lookup = mock(LinkLookupQueryService.class);
  private final LinkRedirectFlow flow = mock(LinkRedirectFlow.class);
  private final LinkPreviewCrawlerDetector crawlers = mock(LinkPreviewCrawlerDetector.class);
  private final LinkPreviewRenderer previews = mock(LinkPreviewRenderer.class);
  private final CustomDomainQueryService domains = mock(CustomDomainQueryService.class);
  private final BlockedDomainChecker blocked = mock(BlockedDomainChecker.class);
  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private final LinkHtmlRenderer html = new LinkHtmlRenderer(messages());
  private final VisitHandoff handoff = new VisitHandoff(new ClientAppClassifier(), html);
  private final RedirectController controller =
      new RedirectController(
          lookup,
          flow,
          mock(ClickRecorder.class),
          crawlers,
          previews,
          new ShortLinkUrlBuilder("https://kurl.me"),
          meters,
          domains,
          new TurnstileProperties("", ""),
          blocked,
          html,
          handoff,
          new VisitResponses(html, handoff),
          Clock.fixed(NOW, ZoneOffset.UTC));
  private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/abc1234");

  private static ResourceBundleMessageSource messages() {
    var ms = new ResourceBundleMessageSource();
    ms.setBasename("messages");
    ms.setDefaultEncoding("UTF-8");
    ms.setFallbackToSystemLocale(false);
    return ms;
  }

  @org.junit.jupiter.api.BeforeEach
  void theMainDomain() {
    when(domains.resolveOwner(any())).thenReturn(null);
  }

  private ResponseEntity<?> visit(String userAgent) {
    return controller.redirect(CODE, null, null, null, userAgent, "ko", request);
  }

  private CachedLinkFixture link() {
    return CachedLinkFixture.link(new LinkId(1L), "https://dest.example")
        .shortCode(CODE)
        .userId(7L);
  }

  private void crawler() {
    when(crawlers.crawlerName(BOT_UA)).thenReturn("Slackbot");
  }

  private static LinkEntity entity() {
    return new LinkEntity("https://dest.example", "abc1234", 7L, null);
  }

  @Test
  void aPasswordLinkPromptsEvenACrawlerAndNeverReadsTheEntity() {
    when(lookup.findActiveLink(CODE)).thenReturn(link().passwordRequired(true).build());
    crawler();

    ResponseEntity<?> response = visit(BOT_UA);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(request.getAttribute(OutcomeResolver.ATTRIBUTE)).isEqualTo("password_required");
    verify(crawlers, never()).crawlerName(any());
    verify(lookup, never()).findEntity(any());
  }

  @Test
  void aCrawlerOnAnOpenLinkGetsTheDestinationPreviewFromOneEntityRead() {
    when(lookup.findActiveLink(CODE)).thenReturn(link().build());
    crawler();
    when(lookup.findEntity(CODE)).thenReturn(Optional.of(entity()));
    when(previews.render(any(), any(), anyLong(), eq(PreviewScope.DESTINATION)))
        .thenReturn("<html>preview</html>");

    ResponseEntity<?> response = visit(BOT_UA);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    verify(lookup, times(1)).findEntity(CODE);
  }

  @Test
  void aCrawlerOnALimitedLinkIsCheckedByTheFlowThenSeesOnlyTheKurlCard() {
    CachedLink limited = link().maxViews(3).build();
    when(lookup.findActiveLink(CODE)).thenReturn(limited);
    crawler();
    LinkEntity entity = entity();
    when(lookup.findEntity(CODE)).thenReturn(Optional.of(entity));
    when(flow.refuseCrawler(eq(limited), eq(entity), any())).thenReturn(Optional.empty());
    when(previews.render(any(), any(), anyLong(), eq(PreviewScope.KURL_CARD_ONLY)))
        .thenReturn("<html>card</html>");

    assertThat(visit(BOT_UA).getStatusCode()).isEqualTo(HttpStatus.OK);
    verify(lookup, times(1)).findEntity(CODE);
  }

  @Test
  void aCrawlerTheFlowRefusesGetsWhatAPersonWould() {
    CachedLink limited = link().blockedCountries("KR").build();
    when(lookup.findActiveLink(CODE)).thenReturn(limited);
    crawler();
    when(lookup.findEntity(CODE)).thenReturn(Optional.empty());
    when(flow.refuseCrawler(eq(limited), eq(null), any()))
        .thenReturn(Optional.of(new RedirectOutcome.CountryBlocked()));

    assertThat(visit(BOT_UA).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void aLinkServedOnSomeoneElsesDomainIsNotFound() {
    when(lookup.findActiveLink(CODE)).thenReturn(link().build());
    when(domains.resolveOwner(any())).thenReturn(99L);

    assertThat(visit("Mozilla/5.0").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(request.getAttribute(OutcomeResolver.ATTRIBUTE)).isEqualTo("not_found");
  }

  @Test
  void aBlockedDestinationIsCountedAndRefused() {
    when(lookup.findActiveLink(CODE)).thenReturn(link().build());
    when(blocked.isBlocked("https://dest.example")).thenReturn(true);

    assertThat(visit("Mozilla/5.0").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(meters.counter("redirect.domain_blocked").count()).isEqualTo(1.0);
  }

  @Test
  void anExpiredLinkWithANextAddressHandsTheVisitorOn() {
    when(lookup.findActiveLink(CODE))
        .thenThrow(new LinkException(LinkErrorCode.LINK_EXPIRED, CODE));
    LinkEntity expired = entity();
    expired.changeExpiredRedirectUrl("https://next.example");
    when(lookup.findEntity(CODE)).thenReturn(Optional.of(expired));

    ResponseEntity<?> response = visit("Mozilla/5.0");

    assertThat(response.getStatusCode().is3xxRedirection()).isTrue();
    assertThat(response.getHeaders().getLocation()).hasToString("https://next.example");
  }

  @Test
  void anExpiredLinkWithNothingToSayShowsTheExpiredPage() {
    when(lookup.findActiveLink(CODE))
        .thenThrow(new LinkException(LinkErrorCode.LINK_EXPIRED, CODE));
    when(lookup.findEntity(CODE)).thenReturn(Optional.of(entity()));

    assertThat(visit("Mozilla/5.0").getStatusCode()).isEqualTo(HttpStatus.GONE);
    assertThat(request.getAttribute(OutcomeResolver.ATTRIBUTE)).isEqualTo("expired");
  }

  @Test
  void aPersonFollowsTheFlow() {
    CachedLink open = link().build();
    when(lookup.findActiveLink(CODE)).thenReturn(open);
    when(flow.execute(eq(open), eq(null), any()))
        .thenReturn(
            new RedirectOutcome.Redirect(new CachedLink.Picked("https://dest.example", null)));

    ResponseEntity<?> response = visit("Mozilla/5.0");

    assertThat(response.getStatusCode().is3xxRedirection()).isTrue();
    verify(lookup, never()).findEntity(any());
  }
}
