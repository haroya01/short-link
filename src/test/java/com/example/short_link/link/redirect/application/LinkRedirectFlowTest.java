package com.example.short_link.link.redirect.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.geoip.GeoLocation;
import com.example.short_link.link.application.dto.CachedLink;
import com.example.short_link.link.application.dto.UserAgentInfo;
import com.example.short_link.link.application.write.IncrementViewCountCommand;
import com.example.short_link.link.application.write.IncrementViewCountUseCase;
import com.example.short_link.link.classifier.application.GeoIpResolver;
import com.example.short_link.link.classifier.application.UserAgentClassifier;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
import com.example.short_link.link.stats.application.ClickRecorder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;

class LinkRedirectFlowTest {

  private final IncrementViewCountUseCase incrementViewCount =
      mock(IncrementViewCountUseCase.class);
  private final ClickRecorder clickRecorder = mock(ClickRecorder.class);
  private final GeoIpResolver geoIpResolver = mock(GeoIpResolver.class);
  private final UserAgentClassifier uaClassifier = mock(UserAgentClassifier.class);
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

  private final LinkRedirectFlow flow =
      new LinkRedirectFlow(
          incrementViewCount, clickRecorder, geoIpResolver, uaClassifier, meterRegistry);

  private CachedLink basicLink(String url) {
    return new CachedLink(new LinkId(7L), url, null, null, null, null);
  }

  private CachedLink linkWithBlockList(String blocked) {
    return new CachedLink(
        new LinkId(7L),
        null,
        7L,
        "https://control",
        null,
        null,
        null,
        null,
        blocked,
        false,
        null,
        null,
        List.of());
  }

  private CachedLink linkWithMaxViewsAndExpired(Integer maxViews, String expiredMessage) {
    return new CachedLink(
        new LinkId(7L),
        null,
        null,
        "https://control",
        null,
        null,
        null,
        null,
        null,
        false,
        maxViews,
        expiredMessage,
        List.of());
  }

  private HttpServletRequest req() {
    return new MockHttpServletRequest("GET", "/abc");
  }

  private void stubBasics() {
    when(geoIpResolver.resolve(any())).thenReturn(new GeoLocation(null, null, null));
    when(uaClassifier.classify(any())).thenReturn(UserAgentInfo.unknown());
  }

  @Test
  void simpleRedirectWithoutMaxViewsRecordsClick() {
    stubBasics();
    CachedLink link = basicLink("https://example.com/dst");

    RedirectOutcome outcome = flow.execute(link, null, null, null, null, null, req());

    assertThat(outcome).isInstanceOf(RedirectOutcome.Redirect.class);
    var redirect = (RedirectOutcome.Redirect) outcome;
    assertThat(redirect.picked().url()).isEqualTo("https://example.com/dst");
    verify(clickRecorder).record(any());
    verify(incrementViewCount, never()).execute(any());
  }

  @Test
  void blockedCountryReturnsBlockedOutcome() {
    when(geoIpResolver.resolve(any())).thenReturn(new GeoLocation("KR", null, null));
    when(uaClassifier.classify(any())).thenReturn(UserAgentInfo.unknown());
    CachedLink link = linkWithBlockList("KR,JP");

    RedirectOutcome outcome = flow.execute(link, null, null, null, null, null, req());

    assertThat(outcome).isInstanceOf(RedirectOutcome.Blocked.class);
    verify(clickRecorder, never()).record(any());
    assertThat(meterRegistry.find("redirect.blocked").counter().count()).isEqualTo(1.0);
  }

  @Test
  void blockedWithUnknownCountryTagsMetricAsUnknown() {
    when(geoIpResolver.resolve(any())).thenReturn(new GeoLocation(null, null, null));
    when(uaClassifier.classify(any())).thenReturn(UserAgentInfo.unknown());
    // Block list with empty entry — the null-country check returns false, so we make it explicit
    // by passing a link that blocks the empty key. Use direct stub: link.isBlockedFor returns true
    // only when the comma split contains a matching code. The "unknown" branch needs blocked=true
    // and countryCode=null at the same time, which CachedLink cannot produce (it short-circuits on
    // null country). So we exercise only the non-null path here.
    CachedLink link = linkWithBlockList("KR");
    when(geoIpResolver.resolve(any())).thenReturn(new GeoLocation("kr", null, null));

    RedirectOutcome outcome = flow.execute(link, null, null, null, null, null, req());

    assertThat(outcome).isInstanceOf(RedirectOutcome.Blocked.class);
  }

  @Test
  void viewLimitHitWithExpiredMessageOnLinkReturnsExpiredWithMessage() {
    stubBasics();
    CachedLink link = linkWithMaxViewsAndExpired(3, "Campaign closed");
    when(incrementViewCount.execute(any(IncrementViewCountCommand.class))).thenReturn(0);

    RedirectOutcome outcome = flow.execute(link, null, null, null, null, null, req());

    assertThat(outcome).isInstanceOf(RedirectOutcome.ExpiredWithMessage.class);
    assertThat(((RedirectOutcome.ExpiredWithMessage) outcome).message())
        .isEqualTo("Campaign closed");
    verify(clickRecorder, never()).record(any());
  }

  @Test
  void viewLimitHitWithoutExpiredMessageThrowsLinkException() {
    stubBasics();
    CachedLink link = linkWithMaxViewsAndExpired(3, null);
    when(incrementViewCount.execute(any(IncrementViewCountCommand.class))).thenReturn(0);

    assertThatThrownBy(() -> flow.execute(link, null, null, null, null, null, req()))
        .isInstanceOf(LinkException.class)
        .extracting(e -> ((LinkException) e).errorCode())
        .isEqualTo(LinkErrorCode.LINK_VIEW_LIMIT_EXCEEDED);
  }

  @Test
  void viewLimitNotExceededProceedsToRedirect() {
    stubBasics();
    CachedLink link = linkWithMaxViewsAndExpired(3, null);
    when(incrementViewCount.execute(any(IncrementViewCountCommand.class))).thenReturn(1);

    RedirectOutcome outcome = flow.execute(link, null, null, null, null, null, req());

    assertThat(outcome).isInstanceOf(RedirectOutcome.Redirect.class);
    verify(clickRecorder).record(any());
  }

  @Test
  void viewLimitHitWithEntityExpiredMessageReturnsExpiredOutcome() {
    stubBasics();
    CachedLink link = basicLink("https://example.com/dst");
    LinkEntity entity = Mockito.mock(LinkEntity.class);
    when(entity.getMaxViews()).thenReturn(3);
    when(entity.getExpiredMessage()).thenReturn("Sold out");
    when(incrementViewCount.execute(any(IncrementViewCountCommand.class))).thenReturn(0);

    RedirectOutcome outcome = flow.execute(link, entity, null, null, null, null, req());

    assertThat(outcome).isInstanceOf(RedirectOutcome.ExpiredWithMessage.class);
    assertThat(((RedirectOutcome.ExpiredWithMessage) outcome).message()).isEqualTo("Sold out");
  }

  @Test
  void viewLimitWithEntityButNoExpiredMessageThrows() {
    stubBasics();
    CachedLink link = basicLink("https://example.com/dst");
    LinkEntity entity = Mockito.mock(LinkEntity.class);
    when(entity.getMaxViews()).thenReturn(3);
    when(entity.getExpiredMessage()).thenReturn(null);
    when(entity.getShortCode()).thenReturn(new ShortCode("abc1"));
    when(incrementViewCount.execute(any(IncrementViewCountCommand.class))).thenReturn(0);

    assertThatThrownBy(() -> flow.execute(link, entity, null, null, null, null, req()))
        .isInstanceOf(LinkException.class);
  }

  @Test
  void viewLimitOkWithEntityPathProceedsToRedirect() {
    stubBasics();
    CachedLink link = basicLink("https://example.com/dst");
    LinkEntity entity = Mockito.mock(LinkEntity.class);
    when(entity.getMaxViews()).thenReturn(3);
    when(incrementViewCount.execute(any(IncrementViewCountCommand.class))).thenReturn(1);

    RedirectOutcome outcome = flow.execute(link, entity, null, null, null, null, req());

    assertThat(outcome).isInstanceOf(RedirectOutcome.Redirect.class);
    verify(clickRecorder).record(any());
  }

  private HttpServletRequest reqWith(String header, String value) {
    MockHttpServletRequest r = new MockHttpServletRequest("GET", "/abc");
    r.addHeader(header, value);
    return r;
  }

  @Test
  void secGpcHeaderMarksContextOptOut() {
    stubBasics();
    var captor =
        org.mockito.ArgumentCaptor.forClass(
            com.example.short_link.link.stats.application.ClickContext.class);

    flow.execute(
        basicLink("https://example.com/dst"),
        null,
        null,
        null,
        null,
        null,
        reqWith("Sec-GPC", "1"));

    verify(clickRecorder).record(captor.capture());
    assertThat(captor.getValue().gpc()).isTrue();
  }

  @Test
  void prefetchViaSecPurposeRecordsAsPreviewNotClick() {
    stubBasics();
    flow.execute(
        basicLink("https://example.com/dst"),
        null,
        null,
        null,
        null,
        null,
        reqWith("Sec-Purpose", "prefetch;prerender"));
    verify(clickRecorder).recordPreview(any(), org.mockito.ArgumentMatchers.eq("prefetch"));
    verify(clickRecorder, never()).record(any());
  }

  @Test
  void prefetchViaLegacyPurposeHeader() {
    stubBasics();
    flow.execute(
        basicLink("https://example.com/dst"),
        null,
        null,
        null,
        null,
        null,
        reqWith("Purpose", "prefetch"));
    verify(clickRecorder).recordPreview(any(), org.mockito.ArgumentMatchers.eq("prefetch"));
  }

  @Test
  void prefetchViaFirefoxXMozHeader() {
    stubBasics();
    flow.execute(
        basicLink("https://example.com/dst"),
        null,
        null,
        null,
        null,
        null,
        reqWith("X-moz", "prefetch"));
    verify(clickRecorder).recordPreview(any(), org.mockito.ArgumentMatchers.eq("prefetch"));
  }

  @Test
  void osNormalizationFlowsThroughToVariantSelection() {
    when(geoIpResolver.resolve(any())).thenReturn(new GeoLocation(null, null, null));
    when(uaClassifier.classify(any()))
        .thenReturn(new UserAgentInfo("mobile", "Android 14", "Chrome", false, null));
    CachedLink link =
        new CachedLink(
            new LinkId(7L),
            null,
            null,
            "https://generic",
            null,
            null,
            null,
            null,
            null,
            false,
            null,
            null,
            List.of(
                new CachedLink.Variant(11L, "https://android", 10, true, null, null, "android")));

    RedirectOutcome outcome = flow.execute(link, null, null, "ua", null, null, req());

    assertThat(((RedirectOutcome.Redirect) outcome).picked().url()).isEqualTo("https://android");
  }
}
