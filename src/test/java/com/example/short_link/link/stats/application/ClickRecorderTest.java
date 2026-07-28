package com.example.short_link.link.stats.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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
import com.example.short_link.link.stats.domain.repository.ClickEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ClickRecorderTest {

  @Mock private ClickEventRepository repository;
  @Mock private UserAgentClassifier userAgentClassifier;
  @Spy private ClientAppClassifier clientAppClassifier;
  @Mock private GeoIpResolver geoIpResolver;
  @Mock private AsnResolver asnResolver;
  @Mock private BotHeuristic botHeuristic;
  @Mock private ApplicationEventPublisher events;
  @InjectMocks private ClickRecorder recorder;

  @BeforeEach
  void stubDefaults() {
    lenient().when(asnResolver.resolve(any())).thenReturn(AsnResolver.AsnInfo.empty());
  }

  private ClickContext ctx(String referrer, String clientIp, String acceptLanguage) {
    return ClickContext.of(
        new LinkId(1L), "https://example.com", referrer, "ua", clientIp, acceptLanguage);
  }

  @Test
  void swallowsRepositoryFailure() {
    when(userAgentClassifier.classify(any())).thenReturn(UserAgentInfo.unknown());
    when(geoIpResolver.resolve(any())).thenReturn(GeoLocation.empty());
    doThrow(new RuntimeException("db down")).when(repository).save(any());

    assertThatNoException().isThrownBy(() -> recorder.record(ctx(null, "1.2.3.4", null)));
  }

  /**
   * A person browsing behind iCloud Private Relay exits through Cloudflare with an ordinary Safari
   * UA. Classifying that as a datacenter bot removed real readers from the human numbers, which is
   * what "someone opened my link and the stats didn't move" was. Relay egress must stay human.
   */
  @Test
  void privacyRelayEgressIsRecordedAsHumanNotBot() {
    when(userAgentClassifier.classify(any())).thenReturn(UserAgentInfo.unknown());
    when(geoIpResolver.resolve(any())).thenReturn(GeoLocation.empty());
    when(asnResolver.resolve(any()))
        .thenReturn(new AsnResolver.AsnInfo(13335, "Cloudflare, Inc.", false, true));
    ArgumentCaptor<ClickEventEntity> captor = ArgumentCaptor.forClass(ClickEventEntity.class);
    when(repository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

    recorder.record(ctx(null, "104.28.0.1", null));

    assertThat(captor.getValue().isBot()).isFalse();
    assertThat(captor.getValue().getBotName()).isNull();
    // The ASN is still stored, so the breakdown can show the traffic came through a relay.
    assertThat(captor.getValue().getAsnOrg()).isEqualTo("Cloudflare, Inc.");
  }

  /**
   * Hosting egress (AWS and friends) with a browser-looking UA stays a bot — that guard is intact.
   */
  @Test
  void datacenterEgressIsStillRecordedAsBot() {
    when(userAgentClassifier.classify(any())).thenReturn(UserAgentInfo.unknown());
    when(geoIpResolver.resolve(any())).thenReturn(GeoLocation.empty());
    when(asnResolver.resolve(any()))
        .thenReturn(new AsnResolver.AsnInfo(16509, "Amazon.com, Inc.", true, false));
    ArgumentCaptor<ClickEventEntity> captor = ArgumentCaptor.forClass(ClickEventEntity.class);
    when(repository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

    recorder.record(ctx(null, "52.94.236.1", null));

    assertThat(captor.getValue().isBot()).isTrue();
    assertThat(captor.getValue().getBotName()).isEqualTo("datacenter:Amazon.com, Inc.");
  }

  /**
   * 인앱 브라우저는 사람이다 — client_app 이 채워져도 봇 판정은 그대로 false 다. 두 축을 섞으면 카카오톡으로 링크를 연 독자가 통계에서 사라진다(프라이버시
   * 릴레이 때 실제로 났던 사고와 같은 모양).
   */
  @Test
  void inAppBrowserIsStoredAsClientAppAndStaysHuman() {
    when(userAgentClassifier.classify(any())).thenReturn(UserAgentInfo.unknown());
    when(geoIpResolver.resolve(any())).thenReturn(GeoLocation.empty());
    ArgumentCaptor<ClickEventEntity> captor = ArgumentCaptor.forClass(ClickEventEntity.class);
    when(repository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));
    String kakao =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like"
            + " Gecko) Mobile/15E148 KAKAOTALK 10.4.5";

    recorder.record(
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
            null));

    assertThat(captor.getValue().getClientApp()).isEqualTo("kakaotalk");
    assertThat(captor.getValue().isBot()).isFalse();
    assertThat(captor.getValue().getBotName()).isNull();
  }

  @Test
  void ordinaryBrowserLeavesClientAppNull() {
    when(userAgentClassifier.classify(any())).thenReturn(UserAgentInfo.unknown());
    when(geoIpResolver.resolve(any())).thenReturn(GeoLocation.empty());
    ArgumentCaptor<ClickEventEntity> captor = ArgumentCaptor.forClass(ClickEventEntity.class);
    when(repository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

    recorder.record(ctx(null, "1.2.3.4", null));

    assertThat(captor.getValue().getClientApp()).isNull();
  }

  /** Sec-Fetch-Site 는 저장만 — 봇 판정에 쓰지 않는다. */
  @Test
  void storesFetchSiteWithoutTouchingBotClassification() {
    when(userAgentClassifier.classify(any())).thenReturn(UserAgentInfo.unknown());
    when(geoIpResolver.resolve(any())).thenReturn(GeoLocation.empty());
    ArgumentCaptor<ClickEventEntity> captor = ArgumentCaptor.forClass(ClickEventEntity.class);
    when(repository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

    recorder.record(ctx(null, "1.2.3.4", null).withFetchSite("none"));

    assertThat(captor.getValue().getFetchSite()).isEqualTo("none");
    assertThat(captor.getValue().isBot()).isFalse();
  }

  @Test
  void fetchSiteStaysNullWhenTheBrowserSendsNothing() {
    when(userAgentClassifier.classify(any())).thenReturn(UserAgentInfo.unknown());
    when(geoIpResolver.resolve(any())).thenReturn(GeoLocation.empty());
    ArgumentCaptor<ClickEventEntity> captor = ArgumentCaptor.forClass(ClickEventEntity.class);
    when(repository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

    recorder.record(ctx(null, "1.2.3.4", null));

    assertThat(captor.getValue().getFetchSite()).isNull();
  }

  @Test
  void normalizesReferrerBeforeSaving() {
    when(userAgentClassifier.classify(any())).thenReturn(UserAgentInfo.unknown());
    when(geoIpResolver.resolve(any())).thenReturn(GeoLocation.empty());
    ArgumentCaptor<ClickEventEntity> captor = ArgumentCaptor.forClass(ClickEventEntity.class);
    when(repository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

    recorder.record(ctx("https://www.youtube.com/watch?v=xyz&token=secret", "1.2.3.4", null));

    assertThat(captor.getValue().getReferrer()).isEqualTo("https://www.youtube.com/watch");
    assertThat(captor.getValue().getReferrerHost()).isEqualTo("www.youtube.com");
  }

  @Test
  void storesMaskedIpNotRaw() {
    when(userAgentClassifier.classify(any())).thenReturn(UserAgentInfo.unknown());
    when(geoIpResolver.resolve(any())).thenReturn(GeoLocation.empty());
    ArgumentCaptor<ClickEventEntity> captor = ArgumentCaptor.forClass(ClickEventEntity.class);
    when(repository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

    recorder.record(ctx(null, "203.0.113.42", null));

    assertThat(captor.getValue().getClientIp()).isEqualTo("203.0.113.*");
    assertThat(captor.getValue().getClientIp()).doesNotContain("42");
  }

  @Test
  void gpcOptOutSkipsVisitorHash() {
    when(userAgentClassifier.classify(any())).thenReturn(UserAgentInfo.unknown());
    when(geoIpResolver.resolve(any())).thenReturn(GeoLocation.empty());
    ArgumentCaptor<ClickEventEntity> captor = ArgumentCaptor.forClass(ClickEventEntity.class);
    when(repository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

    // Sec-GPC 옵트아웃 → 재방문 식별 해시를 만들지 않는다(§0, 측정 아닌 존중).
    recorder.record(ctx(null, "1.2.3.4", null).withGpc(true));

    assertThat(captor.getValue().getVisitorHash()).isNull();
  }

  @Test
  void persistsResolvedGeoLocation() {
    when(userAgentClassifier.classify(any())).thenReturn(UserAgentInfo.unknown());
    when(geoIpResolver.resolve("8.8.8.8"))
        .thenReturn(new GeoLocation("US", "California", "Mountain View"));
    ArgumentCaptor<ClickEventEntity> captor = ArgumentCaptor.forClass(ClickEventEntity.class);
    when(repository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

    recorder.record(ctx(null, "8.8.8.8", "ko-KR,ko;q=0.9"));

    ClickEventEntity saved = captor.getValue();
    assertThat(saved.getCountryCode()).isEqualTo("US");
    assertThat(saved.getRegionName()).isEqualTo("California");
    assertThat(saved.getCityName()).isEqualTo("Mountain View");
    assertThat(saved.getLanguage()).isEqualTo("ko-KR");
    assertThat(saved.getVisitorHash()).hasSize(64);
  }
}
