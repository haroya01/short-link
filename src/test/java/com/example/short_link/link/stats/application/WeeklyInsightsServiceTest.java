package com.example.short_link.link.stats.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.short_link.common.security.UserAccessLookup;
import com.example.short_link.link.application.dto.WeeklyInsights;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.stats.domain.repository.ClickRangeReadRepository;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.HeatmapRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.LinkClickCount;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.UtmSourceClickRow;
import com.example.short_link.support.TestEntities;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WeeklyInsightsServiceTest {

  @Mock private ClickRangeReadRepository clickRepository;
  @Mock private ClickRangeTopReader clickRangeTops;
  @Mock private LinkRepository linkRepository;
  @Mock private UserAccessLookup users;

  private WeeklyInsightsService service;

  @BeforeEach
  void setUp() {
    service = new WeeklyInsightsService(clickRepository, clickRangeTops, linkRepository, users);
  }

  @Test
  void emptyWeekReturnsNullsForDerivedMetrics() {
    when(clickRepository.countByUserIdAndRange(anyLong(), any(), any())).thenReturn(0L);
    when(clickRepository.countHumanByUserIdAndRange(anyLong(), any(), any())).thenReturn(0L);
    when(clickRangeTops.topLinksByUser(anyLong(), any(), any(), anyInt())).thenReturn(List.of());
    when(users.timezone(7L)).thenReturn(Optional.empty());
    when(clickRangeTops.topHeatmapByUser(anyLong(), any(), any(), anyString(), anyInt()))
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
    when(clickRangeTops.topLinksByUser(anyLong(), any(), any(), anyInt())).thenReturn(List.of(top));
    LinkEntity link = new LinkEntity("https://example.com", "abc", 7L, null);
    TestEntities.withId(link, 1L);
    when(linkRepository.findById(1L)).thenReturn(Optional.of(link));
    UtmSourceClickRow source = mock(UtmSourceClickRow.class);
    when(source.getSource()).thenReturn("twitter");
    when(clickRangeTops.topUtmSourcesByLink(anyLong(), any(), any(), anyInt()))
        .thenReturn(List.of(source));
    when(users.timezone(7L)).thenReturn(Optional.of("Asia/Seoul"));
    HeatmapRow peak = mock(HeatmapRow.class);
    when(peak.getDow()).thenReturn(2);
    when(peak.getHour()).thenReturn(21);
    when(peak.getCount()).thenReturn(12L);
    when(clickRangeTops.topHeatmapByUser(anyLong(), any(), any(), anyString(), anyInt()))
        .thenReturn(List.of(peak));

    WeeklyInsights out = service.compute(7L);
    assertThat(out.totalClicks()).isEqualTo(100L);
    assertThat(out.humanClicks()).isEqualTo(80L);
    assertThat(out.previousHumanClicks()).isEqualTo(40L);
    assertThat(out.deltaPercent()).isEqualTo(1.0);
    assertThat(out.humanRatio()).isEqualTo(0.8);
    assertThat(out.topLink()).isNotNull();
    assertThat(out.topLink().shortCode().value()).isEqualTo("abc");
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
    when(clickRangeTops.topLinksByUser(anyLong(), any(), any(), anyInt())).thenReturn(List.of(top));
    when(linkRepository.findById(99L)).thenReturn(Optional.empty());
    when(users.timezone(7L)).thenReturn(Optional.empty());
    when(clickRangeTops.topHeatmapByUser(anyLong(), any(), any(), anyString(), anyInt()))
        .thenReturn(List.of());

    WeeklyInsights out = service.compute(7L);
    assertThat(out.topLink()).isNull();
  }

  @Test
  void recordExposeFields() {
    WeeklyInsights.TopLink tl =
        new WeeklyInsights.TopLink(new ShortCode("abc"), "https://x", 10L, "twitter");
    assertThat(tl.shortCode().value()).isEqualTo("abc");
    assertThat(tl.originalUrl()).isEqualTo("https://x");
    assertThat(tl.clicks()).isEqualTo(10L);
    assertThat(tl.topUtmSource()).isEqualTo("twitter");

    WeeklyInsights.Peak p = new WeeklyInsights.Peak(2, 21, 5L);
    assertThat(p.dayOfWeek()).isEqualTo(2);
    assertThat(p.hour()).isEqualTo(21);
    assertThat(p.clicks()).isEqualTo(5L);
  }
}
