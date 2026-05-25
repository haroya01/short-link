package com.example.short_link.link.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.short_link.link.application.dto.WeeklyInsights;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.ClickEventReadRepository;
import com.example.short_link.link.domain.repository.ClickEventReadRepository.HeatmapRow;
import com.example.short_link.link.domain.repository.ClickEventReadRepository.LinkClickCount;
import com.example.short_link.link.domain.repository.ClickEventReadRepository.UtmSourceClickRow;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.support.TestEntities;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class WeeklyInsightsServiceTest {

  @Mock private ClickEventReadRepository clickRepository;
  @Mock private LinkRepository linkRepository;
  @Mock private UserRepository userRepository;

  private WeeklyInsightsService service;

  @BeforeEach
  void setUp() {
    service = new WeeklyInsightsService(clickRepository, linkRepository, userRepository);
  }

  @Test
  void emptyWeekReturnsNullsForDerivedMetrics() {
    when(clickRepository.countByUserIdAndRange(anyLong(), any(), any())).thenReturn(0L);
    when(clickRepository.countHumanByUserIdAndRange(anyLong(), any(), any())).thenReturn(0L);
    when(clickRepository.findTopLinksByUserIdAndRange(anyLong(), any(), any(), any()))
        .thenReturn(List.of());
    when(userRepository.findById(7L)).thenReturn(Optional.empty());
    when(clickRepository.findHeatmapByUserIdAndRange(anyLong(), any(), any(), any(), any()))
        .thenReturn(List.of());

    WeeklyInsights out = service.compute(7L);
    assertThat(out.totalClicks()).isZero();
    assertThat(out.humanClicks()).isZero();
    assertThat(out.deltaPercent()).isNull();
    assertThat(out.humanRatio()).isNull();
    assertThat(out.topLink()).isNull();
    assertThat(out.peak()).isNull();
  }

  @Test
  void computesDeltaAndHumanRatioAndPeak() {
    when(clickRepository.countByUserIdAndRange(anyLong(), any(), any())).thenReturn(100L);
    when(clickRepository.countHumanByUserIdAndRange(anyLong(), any(), any()))
        .thenReturn(80L)
        .thenReturn(40L);
    LinkClickCount top = mock(LinkClickCount.class);
    when(top.getLinkId()).thenReturn(1L);
    when(top.getCount()).thenReturn(50L);
    when(clickRepository.findTopLinksByUserIdAndRange(anyLong(), any(), any(), any(Pageable.class)))
        .thenReturn(List.of(top));
    LinkEntity link = new LinkEntity("https://example.com", "abc", 7L, null);
    TestEntities.withId(link, 1L);
    when(linkRepository.findById(1L)).thenReturn(Optional.of(link));
    UtmSourceClickRow source = mock(UtmSourceClickRow.class);
    when(source.getSource()).thenReturn("twitter");
    when(clickRepository.findTopUtmSourcesByLinkIdAndRange(
            anyLong(), any(), any(), any(Pageable.class)))
        .thenReturn(List.of(source));
    UserEntity user = new UserEntity("u@x", "google", "g-1");
    user.changeTimezone("Asia/Seoul");
    when(userRepository.findById(7L)).thenReturn(Optional.of(user));
    HeatmapRow peak = mock(HeatmapRow.class);
    when(peak.getDow()).thenReturn(2);
    when(peak.getHour()).thenReturn(21);
    when(peak.getCount()).thenReturn(12L);
    when(clickRepository.findHeatmapByUserIdAndRange(anyLong(), any(), any(), any(), any()))
        .thenReturn(List.of(peak));

    WeeklyInsights out = service.compute(7L);
    assertThat(out.totalClicks()).isEqualTo(100L);
    assertThat(out.humanClicks()).isEqualTo(80L);
    assertThat(out.previousHumanClicks()).isEqualTo(40L);
    assertThat(out.deltaPercent()).isEqualTo(1.0);
    assertThat(out.humanRatio()).isEqualTo(0.8);
    assertThat(out.topLink()).isNotNull();
    assertThat(out.topLink().shortCode()).isEqualTo("abc");
    assertThat(out.topLink().clicks()).isEqualTo(50L);
    assertThat(out.topLink().topUtmSource()).isEqualTo("twitter");
    assertThat(out.peak()).isNotNull();
    assertThat(out.peak().dayOfWeek()).isEqualTo(2);
    assertThat(out.peak().hour()).isEqualTo(21);
    assertThat(out.peak().clicks()).isEqualTo(12L);
  }

  @Test
  void missingLinkReturnsNullTopLink() {
    when(clickRepository.countByUserIdAndRange(anyLong(), any(), any())).thenReturn(5L);
    when(clickRepository.countHumanByUserIdAndRange(anyLong(), any(), any())).thenReturn(5L);
    LinkClickCount top = mock(LinkClickCount.class);
    when(top.getLinkId()).thenReturn(99L);
    when(clickRepository.findTopLinksByUserIdAndRange(anyLong(), any(), any(), any(Pageable.class)))
        .thenReturn(List.of(top));
    when(linkRepository.findById(99L)).thenReturn(Optional.empty());
    when(userRepository.findById(7L)).thenReturn(Optional.empty());
    when(clickRepository.findHeatmapByUserIdAndRange(anyLong(), any(), any(), any(), any()))
        .thenReturn(List.of());

    WeeklyInsights out = service.compute(7L);
    assertThat(out.topLink()).isNull();
  }

  @Test
  void recordExposeFields() {
    WeeklyInsights.TopLink tl = new WeeklyInsights.TopLink("abc", "https://x", 10L, "twitter");
    assertThat(tl.shortCode()).isEqualTo("abc");
    assertThat(tl.originalUrl()).isEqualTo("https://x");
    assertThat(tl.clicks()).isEqualTo(10L);
    assertThat(tl.topUtmSource()).isEqualTo("twitter");

    WeeklyInsights.Peak p = new WeeklyInsights.Peak(2, 21, 5L);
    assertThat(p.dayOfWeek()).isEqualTo(2);
    assertThat(p.hour()).isEqualTo(21);
    assertThat(p.clicks()).isEqualTo(5L);
  }
}
