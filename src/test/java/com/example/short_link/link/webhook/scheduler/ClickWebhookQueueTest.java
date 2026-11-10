package com.example.short_link.link.webhook.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.counter.RedisWindowCounter;
import com.example.short_link.link.application.dto.RecordedClick;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.webhook.domain.LinkWebhookDeliveryEntity;
import com.example.short_link.link.webhook.domain.LinkWebhookEntity;
import com.example.short_link.link.webhook.domain.WebhookDeliveryMode;
import com.example.short_link.link.webhook.domain.WebhookDeliveryStatus;
import com.example.short_link.link.webhook.domain.WebhookFormat;
import com.example.short_link.link.webhook.domain.repository.LinkWebhookDeliveryRepository;
import com.example.short_link.link.webhook.domain.repository.LinkWebhookRepository;
import com.example.short_link.support.TestEntities;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.dao.QueryTimeoutException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

class ClickWebhookQueueTest {

  private static final Instant NOW = Instant.parse("2026-11-10T09:00:00Z");

  private final LinkWebhookRepository hooks = mock(LinkWebhookRepository.class);
  private final RedisWindowCounter counter = mock(RedisWindowCounter.class);
  private final JsonMapper json = JsonMapper.builder().build();
  private final List<LinkWebhookDeliveryEntity> saved = new ArrayList<>();
  private ClickWebhookQueue queue;

  @BeforeEach
  void setUp() {
    LinkWebhookDeliveryRepository deliveries = mock(LinkWebhookDeliveryRepository.class);
    when(deliveries.saveAll(any()))
        .thenAnswer(
            call -> {
              Collection<LinkWebhookDeliveryEntity> rows = call.getArgument(0);
              saved.addAll(rows);
              return List.copyOf(rows);
            });
    queue =
        new ClickWebhookQueue(
            hooks,
            deliveries,
            new WebhookDeliveryGate(new SimpleMeterRegistry(), counter),
            json,
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static LinkWebhookEntity hook(long id, long linkId) {
    return TestEntities.withId(
        new LinkWebhookEntity(
            new LinkId(linkId), "https://example.com/hook", "s", "t", WebhookFormat.GENERIC),
        id);
  }

  private static RecordedClick click(long clickId, long linkId, boolean bot) {
    return new RecordedClick(
        clickId, new LinkId(linkId), NOW, "KR", "Desktop", "google.com", bot, null);
  }

  private Map<String, Object> payload(LinkWebhookDeliveryEntity delivery) {
    return json.readValue(delivery.getPayload(), new TypeReference<Map<String, Object>>() {});
  }

  @Test
  void aClickOnALinkWithoutHooksQueuesNothing() {
    when(hooks.findAllByLinkIdInAndEnabledTrue(Set.of(1L))).thenReturn(List.of());

    queue.enqueue(List.of(click(10L, 1L, false)));

    assertThat(saved).isEmpty();
  }

  @Test
  void everyHookOfTheLinkGetsTheClickWithItsId() {
    when(hooks.findAllByLinkIdInAndEnabledTrue(Set.of(1L, 2L)))
        .thenReturn(List.of(hook(99L, 1L), hook(100L, 1L), hook(101L, 2L)));

    queue.enqueue(List.of(click(10L, 1L, false), click(11L, 2L, false)));

    assertThat(saved)
        .extracting(
            LinkWebhookDeliveryEntity::getHookId,
            LinkWebhookDeliveryEntity::getClickId,
            LinkWebhookDeliveryEntity::getStatus,
            LinkWebhookDeliveryEntity::getNextAttemptAt)
        .containsExactlyInAnyOrder(
            tuple(99L, 10L, WebhookDeliveryStatus.PENDING, NOW),
            tuple(100L, 10L, WebhookDeliveryStatus.PENDING, NOW),
            tuple(101L, 11L, WebhookDeliveryStatus.PENDING, NOW));
  }

  @Test
  void thePayloadKeepsTheClickContractAndAddsTheClickId() {
    when(hooks.findAllByLinkIdInAndEnabledTrue(Set.of(1L))).thenReturn(List.of(hook(99L, 1L)));

    queue.enqueue(List.of(click(10L, 1L, false)));

    assertThat(payload(saved.getFirst()))
        .containsEntry("type", "click")
        .containsEntry("clickId", 10)
        .containsEntry("linkId", 1)
        .containsEntry("occurredAt", NOW.toString())
        .containsEntry("countryCode", "KR")
        .containsEntry("deviceClass", "Desktop")
        .containsEntry("channel", "google.com")
        .containsEntry("utmSource", "")
        .containsEntry("bot", false)
        .doesNotContainKey("referrerHost");
  }

  @Test
  void aClickTheGateRefusesIsNotQueued() {
    when(hooks.findAllByLinkIdInAndEnabledTrue(Set.of(1L))).thenReturn(List.of(hook(99L, 1L)));

    queue.enqueue(List.of(click(10L, 1L, true)));

    assertThat(saved).isEmpty();
  }

  @Test
  void aQuotaCheckThatRedisCannotAnswerSkipsOnlyThatHook() {
    LinkWebhookEntity quoted = hook(99L, 1L);
    quoted.updateConfig(null, null, null, 100, null, null);
    when(hooks.findAllByLinkIdInAndEnabledTrue(Set.of(1L)))
        .thenReturn(List.of(quoted, hook(100L, 1L)));
    when(counter.increment(anyString(), eq(Duration.ofDays(1))))
        .thenThrow(new QueryTimeoutException("Redis command timed out"));

    queue.enqueue(List.of(click(10L, 1L, false)));

    assertThat(saved).extracting(LinkWebhookDeliveryEntity::getHookId).containsExactly(100L);
  }

  @ParameterizedTest
  @EnumSource(WebhookDeliveryMode.class)
  void everyModeKeepsPerClickDelivery(WebhookDeliveryMode mode) {
    LinkWebhookEntity hook = hook(99L, 1L);
    hook.changeDeliveryMode(mode, 9, 50, 10);
    when(hooks.findAllByLinkIdInAndEnabledTrue(Set.of(1L))).thenReturn(List.of(hook));

    queue.enqueue(List.of(click(10L, 1L, false)));

    assertThat(saved).hasSize(1);
  }

  @Test
  void anEmptyFlushLooksNothingUp() {
    queue.enqueue(List.of());

    verify(hooks, never()).findAllByLinkIdInAndEnabledTrue(any());
  }
}
