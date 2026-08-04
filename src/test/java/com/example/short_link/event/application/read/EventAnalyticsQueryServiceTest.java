package com.example.short_link.event.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.short_link.event.application.read.EventAnalyticsView.Bucket;
import com.example.short_link.event.application.read.EventAnalyticsView.DailyBucket;
import com.example.short_link.event.domain.ContactField;
import com.example.short_link.event.domain.EventEntity;
import com.example.short_link.event.domain.EventRegistrationEntity;
import com.example.short_link.event.domain.repository.EventRegistrationRepository;
import com.example.short_link.event.domain.repository.EventRepository;
import com.example.short_link.event.exception.EventErrorCode;
import com.example.short_link.event.exception.EventException;
import com.example.short_link.link.stats.domain.ClickEventEntity;
import com.example.short_link.link.stats.domain.repository.ClickEventRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EventAnalyticsQueryServiceTest {

  @Mock private EventRepository eventRepository;
  @Mock private EventRegistrationRepository registrationRepository;
  @Mock private EventQueryService eventQueryService;
  @Mock private ClickEventRepository clickEventRepository;

  private EventAnalyticsQueryService service;

  @BeforeEach
  void setUp() {
    service =
        new EventAnalyticsQueryService(
            eventRepository, registrationRepository, eventQueryService, clickEventRepository);
  }

  private EventEntity event(String timezone) {
    EventEntity event =
        new EventEntity(
            1L,
            "slug123abc",
            "스터디",
            null,
            Instant.parse("2026-09-01T10:00:00Z"),
            null,
            timezone,
            null,
            null,
            null,
            null,
            null,
            ContactField.EMAIL);
    ReflectionTestUtils.setField(event, "id", 10L);
    return event;
  }

  private EventRegistrationEntity registration(long id, Instant createdAt) {
    EventRegistrationEntity registration =
        new EventRegistrationEntity(10L, "이름" + id, id + "@x.com", null, "hash");
    ReflectionTestUtils.setField(registration, "id", id);
    ReflectionTestUtils.setField(registration, "createdAt", createdAt);
    return registration;
  }

  private ClickEventEntity click(Long linkId, boolean bot, String clientApp) {
    ClickEventEntity click = mock(ClickEventEntity.class);
    when(click.isBot()).thenReturn(bot);
    if (!bot) {
      when(click.getLinkId()).thenReturn(linkId);
      when(click.getClientApp()).thenReturn(clientApp);
    }
    return click;
  }

  @Test
  void missingEvent_isNotFound() {
    when(eventRepository.findById(10L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.analyze(1L, 10L))
        .isInstanceOf(EventException.class)
        .extracting(e -> ((EventException) e).errorCode())
        .isEqualTo(EventErrorCode.EVENT_NOT_FOUND);
  }

  @Test
  void otherOwnersEvent_isDenied() {
    when(eventRepository.findById(10L)).thenReturn(Optional.of(event("Asia/Seoul")));

    assertThatThrownBy(() -> service.analyze(99L, 10L))
        .isInstanceOf(EventException.class)
        .extracting(e -> ((EventException) e).errorCode())
        .isEqualTo(EventErrorCode.EVENT_PERMISSION_DENIED);
  }

  @Test
  void withoutLinks_countsOnlyRegistrations_evenOnBrokenTimezone() {
    when(eventRepository.findById(10L)).thenReturn(Optional.of(event("Not/AZone")));
    when(eventQueryService.channelLabels(10L)).thenReturn(Map.of());
    EventRegistrationEntity canceled = registration(2L, Instant.parse("2026-09-02T02:00:00Z"));
    canceled.cancel(Instant.parse("2026-09-02T03:00:00Z"));
    when(registrationRepository.findAllByEventIdOrderByCreatedAtAsc(10L))
        .thenReturn(List.of(registration(1L, Instant.parse("2026-09-02T01:00:00Z")), canceled));

    EventAnalyticsView view = service.analyze(1L, 10L);

    assertThat(view.totalClicks()).isZero();
    assertThat(view.totalRegistrations()).isEqualTo(1);
    assertThat(view.registrationsByChannel()).containsExactly(new Bucket("direct", 1));
    // Not/AZone 은 Asia/Seoul 로 폴백 — UTC 01시는 서울 10시, 날짜는 그대로 9/2.
    assertThat(view.dailyRegistrations()).containsExactly(new DailyBucket("2026-09-02", 1));
  }

  @Test
  void clicksAndRegistrations_bucketByChannelWithFallbacks() {
    when(eventRepository.findById(10L)).thenReturn(Optional.of(event("Asia/Seoul")));
    Map<Long, String> channels = new LinkedHashMap<>();
    channels.put(7L, "단톡용");
    when(eventQueryService.channelLabels(10L)).thenReturn(channels);
    List<ClickEventEntity> clicks =
        List.of(click(7L, false, "kakaotalk"), click(7L, true, null), click(99L, false, null));
    when(clickEventRepository.findAllByLinkIdInOrderByClickedAtAsc(Set.of(7L))).thenReturn(clicks);

    EventRegistrationEntity byLink = registration(1L, Instant.parse("2026-09-01T01:00:00Z"));
    byLink.attribute(7L, "messenger", "kakaotalk", null, null, null);
    EventRegistrationEntity byApp = registration(2L, Instant.parse("2026-09-01T02:00:00Z"));
    byApp.attribute(null, null, "instagram", null, null, null);
    EventRegistrationEntity byReferrer = registration(3L, Instant.parse("2026-09-02T01:00:00Z"));
    byReferrer.attribute(null, null, null, "twitter.example", null, null);
    when(registrationRepository.findAllByEventIdOrderByCreatedAtAsc(10L))
        .thenReturn(List.of(byLink, byApp, byReferrer));

    EventAnalyticsView view = service.analyze(1L, 10L);

    assertThat(view.totalClicks()).isEqualTo(2);
    assertThat(view.totalRegistrations()).isEqualTo(3);
    assertThat(view.clicksByLink())
        .containsExactlyInAnyOrder(new Bucket("단톡용", 1), new Bucket("?", 1));
    assertThat(view.clicksByClientApp())
        .containsExactlyInAnyOrder(new Bucket("kakaotalk", 1), new Bucket("direct", 1));
    assertThat(view.registrationsByChannel())
        .containsExactlyInAnyOrder(
            new Bucket("단톡용", 1), new Bucket("instagram", 1), new Bucket("twitter.example", 1));
    assertThat(view.dailyRegistrations())
        .containsExactly(new DailyBucket("2026-09-01", 2), new DailyBucket("2026-09-02", 1));
  }
}
