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
  @InjectMocks private ClickRecorder recorder;

  @Test
  void swallowsRepositoryFailure() {
    when(userAgentClassifier.classify(any())).thenReturn(UserAgentInfo.unknown());
    doThrow(new RuntimeException("db down")).when(repository).save(any());

    assertThatNoException()
        .isThrownBy(() -> recorder.record(1L, "https://example.com", null, "ua", "1.2.3.4"));
  }

  @Test
  void normalizesReferrerBeforeSaving() {
    when(userAgentClassifier.classify(any())).thenReturn(UserAgentInfo.unknown());
    org.mockito.ArgumentCaptor<ClickEventEntity> captor =
        org.mockito.ArgumentCaptor.forClass(ClickEventEntity.class);
    when(repository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

    recorder.record(
        1L,
        "https://example.com",
        "https://www.youtube.com/watch?v=xyz&token=secret",
        "ua",
        "1.2.3.4");

    assertThat(captor.getValue().getReferrer()).isEqualTo("https://www.youtube.com/watch");
  }

  @Test
  void persistsResolvedCountryCode() {
    when(userAgentClassifier.classify(any())).thenReturn(UserAgentInfo.unknown());
    when(geoIpResolver.resolveCountry("8.8.8.8")).thenReturn("US");
    org.mockito.ArgumentCaptor<ClickEventEntity> captor =
        org.mockito.ArgumentCaptor.forClass(ClickEventEntity.class);
    when(repository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

    recorder.record(1L, "https://example.com", null, "ua", "8.8.8.8");

    assertThat(captor.getValue().getCountryCode()).isEqualTo("US");
  }
}
