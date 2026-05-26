package com.example.short_link.link.webhook.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.link.application.dto.ClickRecordedEvent;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.stats.domain.repository.ClickAlertReadRepository;
import com.example.short_link.link.stats.domain.repository.ClickTotalsReadRepository;
import com.example.short_link.link.webhook.domain.LinkWebhookEntity;
import com.example.short_link.link.webhook.domain.WebhookDeliveryMode;
import com.example.short_link.link.webhook.domain.repository.LinkWebhookRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ThresholdSpikeDetectorTest {

  private final LinkWebhookRepository hooks = mock(LinkWebhookRepository.class);
  private final LinkRepository links = mock(LinkRepository.class);
  private final ClickTotalsReadRepository clickTotals = mock(ClickTotalsReadRepository.class);
  private final ClickAlertReadRepository clickAlerts = mock(ClickAlertReadRepository.class);
  private final LinkWebhookDispatcher dispatcher = mock(LinkWebhookDispatcher.class);
  private final JsonMapper jsonMapper = JsonMapper.builder().build();

  private final Instant now = Instant.parse("2026-05-25T12:00:00Z");
  private final Clock fixed = Clock.fixed(now, ZoneOffset.UTC);

  private final ThresholdSpikeDetector detector =
      new ThresholdSpikeDetector(
          hooks, links, clickTotals, clickAlerts, dispatcher, jsonMapper, fixed);

  @BeforeEach
  void defaults() {
    lenient()
        .when(clickAlerts.findTopReferrerHostByLinkIdSince(anyLong(), any(Instant.class)))
        .thenReturn(Optional.empty());
  }

  private LinkWebhookEntity hookWithSpike(int threshold, int windowMinutes) {
    LinkWebhookEntity hook =
        new LinkWebhookEntity(new LinkId(1L), "https://example.com/h", "secret", "n");
    hook.changeDeliveryMode(WebhookDeliveryMode.THRESHOLD_SPIKE, null, threshold, windowMinutes);
    return hook;
  }

  private LinkEntity link() {
    return new LinkEntity("https://target", "abc", 7L, null);
  }

  private ClickRecordedEvent click() {
    return new ClickRecordedEvent(new LinkId(1L), now, "KR", "Mobile", "twitter", false, null);
  }

  @Test
  void firesWhenCountReachesThreshold() {
    LinkWebhookEntity hook = hookWithSpike(50, 10);
    when(hooks.findAllEnabledByDeliveryMode(any(), any())).thenReturn(List.of(hook));
    when(links.findById(1L)).thenReturn(Optional.of(link()));
    when(clickTotals.countSinceByLinkId(eq(1L), any(Instant.class))).thenReturn(50L);

    detector.onClickRecorded(click());

    verify(dispatcher, times(1)).deliver(eq(hook), anyString(), eq("spike_alert"));
  }

  @Test
  void doesNotFireBelowThreshold() {
    LinkWebhookEntity hook = hookWithSpike(50, 10);
    when(hooks.findAllEnabledByDeliveryMode(any(), any())).thenReturn(List.of(hook));
    when(clickTotals.countSinceByLinkId(eq(1L), any(Instant.class))).thenReturn(49L);

    detector.onClickRecorded(click());

    verify(dispatcher, never()).deliver(any(), anyString(), anyString());
  }

  @Test
  void doesNotFireOnBotClick() {
    LinkWebhookEntity hook = hookWithSpike(1, 10);
    when(hooks.findAllEnabledByDeliveryMode(any(), any())).thenReturn(List.of(hook));

    detector.onClickRecorded(
        new ClickRecordedEvent(new LinkId(1L), now, "KR", "Mobile", null, true, null));

    verify(dispatcher, never()).deliver(any(), anyString(), anyString());
  }

  @Test
  void doesNotFireWhenInCooldown() {
    LinkWebhookEntity hook = hookWithSpike(50, 10);
    hook.markSpikeFired(now.minusSeconds(60));
    when(hooks.findAllEnabledByDeliveryMode(any(), any())).thenReturn(List.of(hook));

    detector.onClickRecorded(click());

    verify(dispatcher, never()).deliver(any(), anyString(), anyString());
  }

  @Test
  void firesAgainAfterCooldownElapses() {
    LinkWebhookEntity hook = hookWithSpike(50, 10);
    hook.markSpikeFired(now.minusSeconds(11 * 60));
    when(hooks.findAllEnabledByDeliveryMode(any(), any())).thenReturn(List.of(hook));
    when(links.findById(1L)).thenReturn(Optional.of(link()));
    when(clickTotals.countSinceByLinkId(eq(1L), any(Instant.class))).thenReturn(50L);

    detector.onClickRecorded(click());

    verify(dispatcher, times(1)).deliver(eq(hook), anyString(), eq("spike_alert"));
  }

  @Test
  void marksSpikeFiredAfterDelivery() {
    LinkWebhookEntity hook = hookWithSpike(50, 10);
    when(hooks.findAllEnabledByDeliveryMode(any(), any())).thenReturn(List.of(hook));
    when(links.findById(1L)).thenReturn(Optional.of(link()));
    when(clickTotals.countSinceByLinkId(eq(1L), any(Instant.class))).thenReturn(50L);

    detector.onClickRecorded(click());

    assertThat(hook.getSpikeLastFiredAt()).isEqualTo(now);
  }

  @Test
  void skipsHooksForDifferentLink() {
    LinkWebhookEntity otherLinkHook =
        new LinkWebhookEntity(new LinkId(999L), "https://example.com/h2", "secret", "other");
    otherLinkHook.changeDeliveryMode(WebhookDeliveryMode.THRESHOLD_SPIKE, null, 1, 10);
    when(hooks.findAllEnabledByDeliveryMode(any(), any())).thenReturn(List.of(otherLinkHook));

    detector.onClickRecorded(click());

    verify(dispatcher, never()).deliver(any(), anyString(), anyString());
  }

  @Test
  void payloadCarriesWindowString() {
    LinkWebhookEntity hook = hookWithSpike(50, 10);
    when(hooks.findAllEnabledByDeliveryMode(any(), any())).thenReturn(List.of(hook));
    when(links.findById(1L)).thenReturn(Optional.of(link()));
    when(clickTotals.countSinceByLinkId(eq(1L), any(Instant.class))).thenReturn(60L);

    detector.onClickRecorded(click());

    verify(dispatcher, times(1))
        .deliver(eq(hook), contains("\"window\":\"10m\""), eq("spike_alert"));
  }

  @Test
  void emptyCandidatesIsNoOp() {
    when(hooks.findAllEnabledByDeliveryMode(any(), any())).thenReturn(List.of());

    detector.onClickRecorded(click());

    verify(dispatcher, never()).deliver(any(), anyString(), anyString());
  }

  @Test
  void skipsHookWhenLinkMissing() {
    LinkWebhookEntity hook = hookWithSpike(50, 10);
    when(hooks.findAllEnabledByDeliveryMode(any(), any())).thenReturn(List.of(hook));
    when(links.findById(1L)).thenReturn(Optional.empty());
    when(clickTotals.countSinceByLinkId(eq(1L), any(Instant.class))).thenReturn(60L);

    detector.onClickRecorded(click());

    verify(dispatcher, never()).deliver(any(), anyString(), anyString());
  }
}
