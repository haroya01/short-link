package com.example.short_link.link.webhook.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.example.short_link.common.counter.RedisWindowCounter;
import com.example.short_link.link.application.dto.RecordedClick;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.webhook.domain.LinkWebhookEntity;
import com.example.short_link.link.webhook.domain.WebhookFormat;
import com.example.short_link.support.TestEntities;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WebhookDeliveryGateTest {

  @Mock private RedisWindowCounter counter;
  private SimpleMeterRegistry meterRegistry;
  private WebhookDeliveryGate deliveryGate;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    deliveryGate = new WebhookDeliveryGate(meterRegistry, counter);
  }

  private LinkWebhookEntity hook(WebhookFormat format) {
    LinkWebhookEntity h =
        new LinkWebhookEntity(new LinkId(1L), "https://example.com/hook", "secret", "test", format);
    TestEntities.withId(h, 99L);
    return h;
  }

  private RecordedClick event(boolean bot, String channel, String utm) {
    return new RecordedClick(7L, new LinkId(1L), Instant.now(), "KR", "Desktop", channel, bot, utm);
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
    when(counter.increment(anyString(), eq(Duration.ofDays(1)))).thenReturn(101L);
    assertThat(deliveryGate.shouldDeliver(h, event(false, "x", null))).isFalse();
    assertThat(meterRegistry.counter("webhook.delivery", "result", "skipped_quota").count())
        .isEqualTo(1.0);
  }

  @Test
  void shouldDeliverPassesQuotaWhenUnderLimit() {
    LinkWebhookEntity h = hook(WebhookFormat.GENERIC);
    h.updateConfig(null, null, null, 100, null, null);
    when(counter.increment(anyString(), eq(Duration.ofDays(1)))).thenReturn(5L);
    assertThat(deliveryGate.shouldDeliver(h, event(false, "x", null))).isTrue();
  }

  @Test
  void theClickThatReachesTheDailyQuotaIsStillDelivered() {
    LinkWebhookEntity h = hook(WebhookFormat.GENERIC);
    h.updateConfig(null, null, null, 100, null, null);
    when(counter.increment(anyString(), eq(Duration.ofDays(1)))).thenReturn(100L);

    assertThat(deliveryGate.shouldDeliver(h, event(false, "x", null))).isTrue();
  }

  @Test
  void aOnePercentSampleSkipsAlmostEveryClick() {
    LinkWebhookEntity h = hook(WebhookFormat.GENERIC);
    h.updateConfig(null, 1, null, null, null, null);

    long delivered =
        IntStream.range(0, 400)
            .filter(i -> deliveryGate.shouldDeliver(h, event(false, "x", null)))
            .count();

    assertThat(delivered).isLessThan(40);
    assertThat(meterRegistry.counter("webhook.delivery", "result", "skipped_sample").count())
        .isEqualTo(400.0 - delivered);
  }
}
