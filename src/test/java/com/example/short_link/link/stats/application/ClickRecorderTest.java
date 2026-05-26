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
import com.example.short_link.link.classifier.application.GeoIpResolver;
import com.example.short_link.link.classifier.application.UserAgentClassifier;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.stats.domain.ClickEventEntity;
import com.example.short_link.link.stats.domain.repository.ClickEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClickRecorderTest {

  @Mock private ClickEventRepository repository;
  @Mock private UserAgentClassifier userAgentClassifier;
  @Mock private GeoIpResolver geoIpResolver;
  @Mock private AsnResolver asnResolver;
  @Mock private BotHeuristic botHeuristic;
  @Mock private org.springframework.context.ApplicationEventPublisher events;
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

  @Test
  void normalizesReferrerBeforeSaving() {
    when(userAgentClassifier.classify(any())).thenReturn(UserAgentInfo.unknown());
    when(geoIpResolver.resolve(any())).thenReturn(GeoLocation.empty());
    org.mockito.ArgumentCaptor<ClickEventEntity> captor =
        org.mockito.ArgumentCaptor.forClass(ClickEventEntity.class);
    when(repository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

    recorder.record(ctx("https://www.youtube.com/watch?v=xyz&token=secret", "1.2.3.4", null));

    assertThat(captor.getValue().getReferrer()).isEqualTo("https://www.youtube.com/watch");
    assertThat(captor.getValue().getReferrerHost()).isEqualTo("www.youtube.com");
  }

  @Test
  void storesMaskedIpNotRaw() {
    when(userAgentClassifier.classify(any())).thenReturn(UserAgentInfo.unknown());
    when(geoIpResolver.resolve(any())).thenReturn(GeoLocation.empty());
    org.mockito.ArgumentCaptor<ClickEventEntity> captor =
        org.mockito.ArgumentCaptor.forClass(ClickEventEntity.class);
    when(repository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

    recorder.record(ctx(null, "203.0.113.42", null));

    assertThat(captor.getValue().getClientIp()).isEqualTo("203.0.113.*");
    assertThat(captor.getValue().getClientIp()).doesNotContain("42");
  }

  @Test
  void persistsResolvedGeoLocation() {
    when(userAgentClassifier.classify(any())).thenReturn(UserAgentInfo.unknown());
    when(geoIpResolver.resolve("8.8.8.8"))
        .thenReturn(new GeoLocation("US", "California", "Mountain View"));
    org.mockito.ArgumentCaptor<ClickEventEntity> captor =
        org.mockito.ArgumentCaptor.forClass(ClickEventEntity.class);
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
