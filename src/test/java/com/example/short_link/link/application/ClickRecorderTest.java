package com.example.short_link.link.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.example.short_link.link.domain.ClickEventEntity;
import com.example.short_link.link.domain.ClickEventRepository;
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
  @Mock private BotHeuristic botHeuristic;
  @InjectMocks private ClickRecorder recorder;

  @Test
  void swallowsRepositoryFailure() {
    when(userAgentClassifier.classify(any())).thenReturn(UserAgentInfo.unknown());
    when(geoIpResolver.resolve(any())).thenReturn(GeoLocation.empty());
    doThrow(new RuntimeException("db down")).when(repository).save(any());

    assertThatNoException()
        .isThrownBy(() -> recorder.record(1L, "https://example.com", null, "ua", "1.2.3.4", null));
  }

  @Test
  void normalizesReferrerBeforeSaving() {
    when(userAgentClassifier.classify(any())).thenReturn(UserAgentInfo.unknown());
    when(geoIpResolver.resolve(any())).thenReturn(GeoLocation.empty());
    org.mockito.ArgumentCaptor<ClickEventEntity> captor =
        org.mockito.ArgumentCaptor.forClass(ClickEventEntity.class);
    when(repository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

    recorder.record(
        1L,
        "https://example.com",
        "https://www.youtube.com/watch?v=xyz&token=secret",
        "ua",
        "1.2.3.4",
        null);

    assertThat(captor.getValue().getReferrer()).isEqualTo("https://www.youtube.com/watch");
    assertThat(captor.getValue().getReferrerHost()).isEqualTo("www.youtube.com");
  }

  @Test
  void persistsResolvedGeoLocation() {
    when(userAgentClassifier.classify(any())).thenReturn(UserAgentInfo.unknown());
    when(geoIpResolver.resolve("8.8.8.8"))
        .thenReturn(new GeoLocation("US", "California", "Mountain View"));
    org.mockito.ArgumentCaptor<ClickEventEntity> captor =
        org.mockito.ArgumentCaptor.forClass(ClickEventEntity.class);
    when(repository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

    recorder.record(1L, "https://example.com", null, "ua", "8.8.8.8", "ko-KR,ko;q=0.9");

    ClickEventEntity saved = captor.getValue();
    assertThat(saved.getCountryCode()).isEqualTo("US");
    assertThat(saved.getRegionName()).isEqualTo("California");
    assertThat(saved.getCityName()).isEqualTo("Mountain View");
    assertThat(saved.getLanguage()).isEqualTo("ko-KR");
    assertThat(saved.getVisitorHash()).hasSize(64);
  }
}
