package com.example.short_link.link.stats.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.example.short_link.common.geoip.GeoLocation;
import com.example.short_link.link.application.dto.UserAgentInfo;
import com.example.short_link.link.classifier.application.AsnResolver;
import com.example.short_link.link.classifier.application.BotHeuristic;
import com.example.short_link.link.classifier.application.ClientAppClassifier;
import com.example.short_link.link.classifier.application.GeoIpResolver;
import com.example.short_link.link.classifier.application.UserAgentClassifier;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.stats.domain.ClickEventEntity;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClickEventAssemblerTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-08-09T05:00:00Z");

  @Mock private UserAgentClassifier userAgentClassifier;
  @Spy private ClientAppClassifier clientAppClassifier;
  @Mock private GeoIpResolver geoIpResolver;
  @Mock private AsnResolver asnResolver;
  @Mock private BotHeuristic botHeuristic;
  @InjectMocks private ClickEventAssembler assembler;

  @BeforeEach
  void stubDefaults() {
    lenient().when(asnResolver.resolve(any())).thenReturn(AsnResolver.AsnInfo.empty());
    lenient().when(userAgentClassifier.classify(any())).thenReturn(UserAgentInfo.unknown());
    lenient().when(geoIpResolver.resolve(any())).thenReturn(GeoLocation.empty());
  }

  private ClickContext ctx(String referrer, String clientIp, String acceptLanguage) {
    return ClickContext.of(
        new LinkId(1L), "https://example.com", referrer, "ua", clientIp, acceptLanguage);
  }

  private ClickEventEntity assemble(ClickContext ctx) {
    return assembler.assemble(new PendingClick(ctx, null, OCCURRED_AT));
  }

  @Test
  void stampsClickedAtFromEnqueueTimeNotAssemblyTime() {
    ClickEventEntity event = assemble(ctx(null, "1.2.3.4", null));

    assertThat(event.getClickedAt()).isEqualTo(OCCURRED_AT);
  }

  @Test
  void forcedBotNameMarksRowAsBotVerbatim() {
    ClickEventEntity event =
        assembler.assemble(
            new PendingClick(ctx(null, "1.2.3.4", null), "preview:prefetch", OCCURRED_AT));

    assertThat(event.isBot()).isTrue();
    assertThat(event.getBotName()).isEqualTo("preview:prefetch");
  }

  @Test
  void privacyRelayEgressIsRecordedAsHumanNotBot() {
    when(asnResolver.resolve(any()))
        .thenReturn(new AsnResolver.AsnInfo(13335, "Cloudflare, Inc.", false, true));

    ClickEventEntity event = assemble(ctx(null, "104.28.0.1", null));

    assertThat(event.isBot()).isFalse();
    assertThat(event.getBotName()).isNull();
    assertThat(event.getAsnOrg()).isEqualTo("Cloudflare, Inc.");
  }

  @Test
  void datacenterEgressIsStillRecordedAsBot() {
    when(asnResolver.resolve(any()))
        .thenReturn(new AsnResolver.AsnInfo(16509, "Amazon.com, Inc.", true, false));

    ClickEventEntity event = assemble(ctx(null, "52.94.236.1", null));

    assertThat(event.isBot()).isTrue();
    assertThat(event.getBotName()).isEqualTo("datacenter:Amazon.com, Inc.");
  }

  @Test
  void inAppBrowserIsStoredAsClientAppAndStaysHuman() {
    String kakao =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like"
            + " Gecko) Mobile/15E148 KAKAOTALK 10.4.5";

    ClickEventEntity event =
        assembler.assemble(
            new PendingClick(
                new ClickContext(
                    new LinkId(1L),
                    "https://example.com",
                    null,
                    kakao,
                    "1.2.3.4",
                    null,
                    null,
                    null,
                    null,
                    false,
                    null),
                null,
                OCCURRED_AT));

    assertThat(event.getClientApp()).isEqualTo("kakaotalk");
    assertThat(event.isBot()).isFalse();
    assertThat(event.getBotName()).isNull();
  }

  @Test
  void ordinaryBrowserLeavesClientAppNull() {
    ClickEventEntity event = assemble(ctx(null, "1.2.3.4", null));

    assertThat(event.getClientApp()).isNull();
  }

  @Test
  void storesFetchSiteWithoutTouchingBotClassification() {
    ClickEventEntity event =
        assembler.assemble(
            new PendingClick(ctx(null, "1.2.3.4", null).withFetchSite("none"), null, OCCURRED_AT));

    assertThat(event.getFetchSite()).isEqualTo("none");
    assertThat(event.isBot()).isFalse();
  }

  @Test
  void fetchSiteStaysNullWhenTheBrowserSendsNothing() {
    ClickEventEntity event = assemble(ctx(null, "1.2.3.4", null));

    assertThat(event.getFetchSite()).isNull();
  }

  @Test
  void normalizesReferrerBeforeSaving() {
    ClickEventEntity event =
        assemble(ctx("https://www.youtube.com/watch?v=xyz&token=secret", "1.2.3.4", null));

    assertThat(event.getReferrer()).isEqualTo("https://www.youtube.com/watch");
    assertThat(event.getReferrerHost()).isEqualTo("www.youtube.com");
  }

  @Test
  void storesMaskedIpNotRaw() {
    ClickEventEntity event = assemble(ctx(null, "203.0.113.42", null));

    assertThat(event.getClientIp()).isEqualTo("203.0.113.*");
    assertThat(event.getClientIp()).doesNotContain("42");
  }

  @Test
  void gpcOptOutSkipsVisitorHash() {
    ClickEventEntity event =
        assembler.assemble(
            new PendingClick(ctx(null, "1.2.3.4", null).withGpc(true), null, OCCURRED_AT));

    assertThat(event.getVisitorHash()).isNull();
  }

  @Test
  void persistsResolvedGeoLocation() {
    when(geoIpResolver.resolve("8.8.8.8"))
        .thenReturn(new GeoLocation("US", "California", "Mountain View"));

    ClickEventEntity event = assemble(ctx(null, "8.8.8.8", "ko-KR,ko;q=0.9"));

    assertThat(event.getCountryCode()).isEqualTo("US");
    assertThat(event.getRegionName()).isEqualTo("California");
    assertThat(event.getCityName()).isEqualTo("Mountain View");
    assertThat(event.getLanguage()).isEqualTo("ko-KR");
    assertThat(event.getVisitorHash()).hasSize(64);
  }
}
