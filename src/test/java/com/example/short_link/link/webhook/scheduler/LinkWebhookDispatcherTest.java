package com.example.short_link.link.webhook.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.link.application.dto.ClickRecordedEvent;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.webhook.domain.LinkWebhookEntity;
import com.example.short_link.link.webhook.domain.WebhookFormat;
import com.example.short_link.link.webhook.domain.repository.LinkWebhookRepository;
import com.example.short_link.support.TestEntities;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class LinkWebhookDispatcherTest {

  @Mock private LinkWebhookRepository repository;
  @Mock private StringRedisTemplate redis;

  private SimpleMeterRegistry meterRegistry;
  private JsonMapper jsonMapper;
  private WebhookDeliveryGate deliveryGate;
  private WebhookBatchBuffer batchBuffer;
  private WebhookHttpDeliveryClient deliveryClient;
  private LinkWebhookDispatcher dispatcher;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    jsonMapper = JsonMapper.builder().build();
    deliveryGate = new WebhookDeliveryGate(meterRegistry, redis);
    batchBuffer = new WebhookBatchBuffer(meterRegistry);
    deliveryClient = mock(WebhookHttpDeliveryClient.class);
    dispatcher =
        new LinkWebhookDispatcher(
            repository, jsonMapper, deliveryGate, batchBuffer, deliveryClient);
  }

  private LinkWebhookEntity hook(WebhookFormat format) {
    LinkWebhookEntity h =
        new LinkWebhookEntity(new LinkId(1L), "https://example.com/hook", "secret", "test", format);
    TestEntities.withId(h, 99L);
    return h;
  }

  private ClickRecordedEvent event(boolean bot, String channel, String utm) {
    return new ClickRecordedEvent(
        new LinkId(1L), Instant.now(), "KR", "Desktop", channel, bot, utm);
  }

  @Test
  void noHooksReturnsEarly() {
    when(repository.findAllByLinkIdAndEnabledTrue(1L)).thenReturn(List.of());
    dispatcher.onClickRecorded(event(false, "google.com", "google"));
    verify(repository).findAllByLinkIdAndEnabledTrue(1L);
  }

  @Test
  void shouldDeliverFalseWhenBotAndIncludeBotsOff() {
    LinkWebhookEntity h = hook(WebhookFormat.GENERIC);
    assertThat(deliveryGate.shouldDeliver(h, event(true, "x", null))).isFalse();
    assertThat(meterRegistry.counter("webhook.delivery", "result", "skipped_bot").count())
        .isEqualTo(1.0);
  }

  @Test
  void shouldDeliverFalseWhenReferrerFilterMismatches() {
    LinkWebhookEntity h = hook(WebhookFormat.GENERIC);
    h.updateConfig(null, null, null, null, "twitter", null);
    assertThat(deliveryGate.shouldDeliver(h, event(false, "google.com", null))).isFalse();
    assertThat(meterRegistry.counter("webhook.delivery", "result", "skipped_filter").count())
        .isEqualTo(1.0);
  }

  @Test
  void shouldDeliverFalseWhenUtmSourceFilterMismatches() {
    LinkWebhookEntity h = hook(WebhookFormat.GENERIC);
    h.updateConfig(null, null, null, null, null, "twitter");
    assertThat(deliveryGate.shouldDeliver(h, event(false, "x.com", "google"))).isFalse();
  }

  @Test
  void shouldDeliverTrueWhenFiltersMatch() {
    LinkWebhookEntity h = hook(WebhookFormat.GENERIC);
    h.updateConfig(null, null, null, null, "twitter", "twitter");
    assertThat(deliveryGate.shouldDeliver(h, event(false, "twitter.com", "twitter"))).isTrue();
  }

  @Test
  void shouldDeliverTrueWhenSampleRate100() {
    LinkWebhookEntity h = hook(WebhookFormat.GENERIC);
    assertThat(deliveryGate.shouldDeliver(h, event(false, "x", null))).isTrue();
  }

  @Test
  void shouldDeliverConsultsQuotaWhenSet() {
    LinkWebhookEntity h = hook(WebhookFormat.GENERIC);
    h.updateConfig(null, null, null, 100, null, null);
    when(redis.execute(any(RedisScript.class), anyList(), eq("86400"))).thenReturn(101L);
    assertThat(deliveryGate.shouldDeliver(h, event(false, "x", null))).isFalse();
    assertThat(meterRegistry.counter("webhook.delivery", "result", "skipped_quota").count())
        .isEqualTo(1.0);
  }

  @Test
  void shouldDeliverPassesQuotaWhenUnderLimit() {
    LinkWebhookEntity h = hook(WebhookFormat.GENERIC);
    h.updateConfig(null, null, null, 100, null, null);
    when(redis.execute(any(RedisScript.class), anyList(), eq("86400"))).thenReturn(5L);
    assertThat(deliveryGate.shouldDeliver(h, event(false, "x", null))).isTrue();
  }

  @Test
  void enqueueBatchDropsWhenOverflow() {
    for (int i = 0; i < 500; i++) batchBuffer.enqueue(99L, Map.of("i", i));
    batchBuffer.enqueue(99L, Map.of("type", "click"));
    assertThat(batchBuffer.size(99L)).isEqualTo(500);
    assertThat(meterRegistry.counter("webhook.delivery", "result", "dropped_overflow").count())
        .isEqualTo(1.0);
  }

  @Test
  void enqueueBatchAppendsWhenUnderCap() {
    batchBuffer.enqueue(99L, Map.of("type", "click"));
    assertThat(batchBuffer.size(99L)).isEqualTo(1);
  }

  @Test
  void flushBatchesSkipsEmptyOrDisabled() {
    batchBuffer.enqueue(99L, Map.of("type", "click"));
    batchBuffer.drain(99L);
    dispatcher.flushBatches();
    verify(repository, never()).findById(any());
  }

  @Test
  void flushBatchesClearsQueueWhenHookGoneOrDisabled() {
    batchBuffer.enqueue(99L, Map.of("type", "click"));
    when(repository.findById(99L)).thenReturn(Optional.empty());
    dispatcher.flushBatches();
    assertThat(batchBuffer.size(99L)).isZero();
  }

  @Test
  void flushBatchesClearsWhenBatchDisabled() {
    LinkWebhookEntity h = hook(WebhookFormat.GENERIC);
    batchBuffer.enqueue(99L, Map.of("type", "click"));
    when(repository.findById(99L)).thenReturn(Optional.of(h));
    dispatcher.flushBatches();
    assertThat(batchBuffer.size(99L)).isZero();
  }
}
