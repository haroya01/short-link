package com.example.short_link.notification.infrastructure.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.lock.RedisDistributedLock;
import com.example.short_link.link.application.dto.ClickRecordedEvent;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.stats.domain.repository.ClickRangeReadRepository;
import com.example.short_link.link.stats.domain.repository.ClickTotalsReadRepository;
import com.example.short_link.notification.application.link.LinkNotificationDispatcher;
import com.example.short_link.notification.application.link.LinkNotificationProperties;
import com.example.short_link.notification.domain.LinkNotificationType;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LinkClickNotificationListenerTest {

  private final LinkRepository links = mock(LinkRepository.class);
  private final ClickTotalsReadRepository totals = mock(ClickTotalsReadRepository.class);
  private final ClickRangeReadRepository ranges = mock(ClickRangeReadRepository.class);
  private final LinkNotificationDispatcher dispatcher = mock(LinkNotificationDispatcher.class);
  private final RedisDistributedLock cooldown = mock(RedisDistributedLock.class);
  private final LinkNotificationProperties props =
      new LinkNotificationProperties(true, null, true, null, 7, 10, 3.0);

  private final LinkClickNotificationListener listener =
      new LinkClickNotificationListener(links, totals, ranges, dispatcher, props, cooldown);

  private ClickRecordedEvent event(boolean bot) {
    return new ClickRecordedEvent(
        LinkId.of(1L), Instant.now(), "KR", "mobile", "direct", bot, null);
  }

  private LinkEntity link() {
    // 실제 엔티티(공개 생성자) — 목으로 만들면 게터 스터빙이 UnfinishedStubbing 으로 깨진다.
    return new LinkEntity("https://blog.example.com/spring", "spring", 5L, null);
  }

  @Test
  void botClickIsIgnored() {
    listener.onClickRecorded(event(true));
    verify(dispatcher, never()).dispatch(any(), any(), any(), any(), any());
  }

  @Test
  void missingLinkIsIgnored() {
    when(links.findById(1L)).thenReturn(Optional.empty());
    listener.onClickRecorded(event(false));
    verify(dispatcher, never()).dispatch(any(), any(), any(), any(), any());
  }

  @Test
  void firstClickNotifies() {
    when(links.findById(1L)).thenReturn(Optional.of(link()));
    when(totals.countHumanByLinkId(1L)).thenReturn(1L);
    listener.onClickRecorded(event(false));
    verify(dispatcher)
        .dispatch(
            eq(5L), eq(LinkNotificationType.FIRST_CLICK), anyString(), anyString(), anyString());
  }

  @Test
  void milestoneNotifies() {
    when(links.findById(1L)).thenReturn(Optional.of(link()));
    when(totals.countHumanByLinkId(1L)).thenReturn(100L);
    listener.onClickRecorded(event(false));
    verify(dispatcher)
        .dispatch(
            eq(5L), eq(LinkNotificationType.MILESTONE), anyString(), anyString(), anyString());
  }

  @Test
  void velocitySpikeNotifiesWhenAboveBaselineAndCooldownFree() {
    when(links.findById(1L)).thenReturn(Optional.of(link()));
    when(totals.countHumanByLinkId(1L)).thenReturn(50L); // not a milestone, >= floor
    when(ranges.countHumanByLinkIdAndRange(eq(1L), any(), any()))
        .thenReturn(20L, 24L); // current, prior
    when(cooldown.tryAcquire(anyString(), any(Duration.class))).thenReturn(true);
    listener.onClickRecorded(event(false));
    verify(dispatcher)
        .dispatch(
            eq(5L), eq(LinkNotificationType.VELOCITY_SPIKE), anyString(), anyString(), anyString());
  }

  @Test
  void velocityBelowThresholdDoesNotNotify() {
    when(links.findById(1L)).thenReturn(Optional.of(link()));
    when(totals.countHumanByLinkId(1L)).thenReturn(50L);
    when(ranges.countHumanByLinkIdAndRange(eq(1L), any(), any())).thenReturn(2L); // current < 3
    listener.onClickRecorded(event(false));
    verify(dispatcher, never()).dispatch(any(), any(), any(), any(), any());
  }

  @Test
  void velocityCooldownBlocksRepeat() {
    when(links.findById(1L)).thenReturn(Optional.of(link()));
    when(totals.countHumanByLinkId(1L)).thenReturn(50L);
    when(ranges.countHumanByLinkIdAndRange(eq(1L), any(), any())).thenReturn(20L, 0L);
    when(cooldown.tryAcquire(anyString(), any(Duration.class))).thenReturn(false);
    listener.onClickRecorded(event(false));
    verify(dispatcher, never()).dispatch(any(), any(), any(), any(), any());
  }
}
