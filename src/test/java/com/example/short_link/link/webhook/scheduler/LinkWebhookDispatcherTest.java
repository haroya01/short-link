package com.example.short_link.link.webhook.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.net.HttpFetcher;
import com.example.short_link.link.application.dto.ClickRecordedEvent;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.webhook.domain.LinkWebhookEntity;
import com.example.short_link.link.webhook.domain.WebhookFormat;
import com.example.short_link.link.webhook.domain.repository.LinkWebhookRepository;
import com.example.short_link.support.TestEntities;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
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
  private LinkWebhookDispatcher dispatcher;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    jsonMapper = JsonMapper.builder().build();
    dispatcher =
        new LinkWebhookDispatcher(
            repository, jsonMapper, meterRegistry, redis, mock(HttpFetcher.class));
  }

  private LinkWebhookEntity hook(WebhookFormat format) {
    LinkWebhookEntity h =
        new LinkWebhookEntity(1L, "https://example.com/hook", "secret", "test", format);
    TestEntities.withId(h, 99L);
    return h;
  }

  private ClickRecordedEvent event(boolean bot, String channel, String utm) {
    return new ClickRecordedEvent(1L, Instant.now(), "KR", "Desktop", channel, bot, utm);
  }

  @Test
  void noHooksReturnsEarly() {
    when(repository.findAllByLinkIdAndEnabledTrue(1L)).thenReturn(List.of());
    dispatcher.onClickRecorded(event(false, "google.com", "google"));
    verify(repository).findAllByLinkIdAndEnabledTrue(1L);
  }

  @Test
  void deliverBlocksWhenUrlNotPublic() {
    LinkWebhookEntity h =
        new LinkWebhookEntity(1L, "http://localhost/hook", "secret", "n", WebhookFormat.GENERIC);
    TestEntities.withId(h, 99L);
    dispatcher.deliver(h, "{\"a\":1}", "click");
    assertThat(meterRegistry.counter("webhook.delivery", "result", "blocked").count())
        .isEqualTo(1.0);
    assertThat(h.getLastError()).contains("public host");
  }

  @Test
  void shouldDeliverFalseWhenBotAndIncludeBotsOff() throws Exception {
    LinkWebhookEntity h = hook(WebhookFormat.GENERIC);
    assertThat(callShouldDeliver(h, event(true, "x", null))).isFalse();
    assertThat(meterRegistry.counter("webhook.delivery", "result", "skipped_bot").count())
        .isEqualTo(1.0);
  }

  @Test
  void shouldDeliverFalseWhenReferrerFilterMismatches() throws Exception {
    LinkWebhookEntity h = hook(WebhookFormat.GENERIC);
    h.updateConfig(null, null, null, null, "twitter", null);
    assertThat(callShouldDeliver(h, event(false, "google.com", null))).isFalse();
    assertThat(meterRegistry.counter("webhook.delivery", "result", "skipped_filter").count())
        .isEqualTo(1.0);
  }

  @Test
  void shouldDeliverFalseWhenUtmSourceFilterMismatches() throws Exception {
    LinkWebhookEntity h = hook(WebhookFormat.GENERIC);
    h.updateConfig(null, null, null, null, null, "twitter");
    assertThat(callShouldDeliver(h, event(false, "x.com", "google"))).isFalse();
  }

  @Test
  void shouldDeliverTrueWhenFiltersMatch() throws Exception {
    LinkWebhookEntity h = hook(WebhookFormat.GENERIC);
    h.updateConfig(null, null, null, null, "twitter", "twitter");
    assertThat(callShouldDeliver(h, event(false, "twitter.com", "twitter"))).isTrue();
  }

  @Test
  void shouldDeliverTrueWhenSampleRate100() throws Exception {
    LinkWebhookEntity h = hook(WebhookFormat.GENERIC);
    assertThat(callShouldDeliver(h, event(false, "x", null))).isTrue();
  }

  @Test
  void shouldDeliverConsultsQuotaWhenSet() throws Exception {
    LinkWebhookEntity h = hook(WebhookFormat.GENERIC);
    h.updateConfig(null, null, null, 100, null, null);
    when(redis.execute(any(RedisScript.class), anyList(), eq(new ShortCode("86400"))))
        .thenReturn(101L);
    assertThat(callShouldDeliver(h, event(false, "x", null))).isFalse();
    assertThat(meterRegistry.counter("webhook.delivery", "result", "skipped_quota").count())
        .isEqualTo(1.0);
  }

  @Test
  void shouldDeliverPassesQuotaWhenUnderLimit() throws Exception {
    LinkWebhookEntity h = hook(WebhookFormat.GENERIC);
    h.updateConfig(null, null, null, 100, null, null);
    when(redis.execute(any(RedisScript.class), anyList(), eq(new ShortCode("86400"))))
        .thenReturn(5L);
    assertThat(callShouldDeliver(h, event(false, "x", null))).isTrue();
  }

  @Test
  void enqueueBatchDropsWhenOverflow() throws Exception {
    ConcurrentHashMap<Long, ConcurrentLinkedQueue<Map<String, Object>>> queues = batchQueues();
    ConcurrentLinkedQueue<Map<String, Object>> q = new ConcurrentLinkedQueue<>();
    for (int i = 0; i < 500; i++) q.add(Map.of("i", i));
    queues.put(99L, q);
    invokeEnqueueBatch(99L, Map.of("type", "click"));
    assertThat(q).hasSize(500);
    assertThat(meterRegistry.counter("webhook.delivery", "result", "dropped_overflow").count())
        .isEqualTo(1.0);
  }

  @Test
  void enqueueBatchAppendsWhenUnderCap() throws Exception {
    invokeEnqueueBatch(99L, Map.of("type", "click"));
    ConcurrentHashMap<Long, ConcurrentLinkedQueue<Map<String, Object>>> queues = batchQueues();
    assertThat(queues.get(99L)).hasSize(1);
  }

  @Test
  void flushBatchesSkipsEmptyOrDisabled() {
    batchQueues().put(99L, new ConcurrentLinkedQueue<>());
    dispatcher.flushBatches();
    verify(repository, never()).findById(any());
  }

  @Test
  void flushBatchesClearsQueueWhenHookGoneOrDisabled() {
    ConcurrentLinkedQueue<Map<String, Object>> q = new ConcurrentLinkedQueue<>();
    q.add(Map.of("type", "click"));
    batchQueues().put(99L, q);
    when(repository.findById(99L)).thenReturn(Optional.empty());
    dispatcher.flushBatches();
    assertThat(q).isEmpty();
  }

  @Test
  void flushBatchesClearsWhenBatchDisabled() {
    LinkWebhookEntity h = hook(WebhookFormat.GENERIC);
    ConcurrentLinkedQueue<Map<String, Object>> q = new ConcurrentLinkedQueue<>();
    q.add(Map.of("type", "click"));
    batchQueues().put(99L, q);
    when(repository.findById(99L)).thenReturn(Optional.of(h));
    dispatcher.flushBatches();
    assertThat(q).isEmpty();
  }

  @Test
  void deliverFailsToSignWhenSecretInvalid() {
    LinkWebhookEntity h =
        new LinkWebhookEntity(1L, "https://example.com/hook", null, "n", WebhookFormat.GENERIC);
    TestEntities.withId(h, 99L);
    dispatcher.deliver(h, "{\"a\":1}", "click");
    assertThat(meterRegistry.counter("webhook.delivery", "result", "sign_error").count())
        .isEqualTo(1.0);
    assertThat(h.getLastError()).contains("signature failed");
  }

  @SuppressWarnings("unchecked")
  private ConcurrentHashMap<Long, ConcurrentLinkedQueue<Map<String, Object>>> batchQueues() {
    try {
      Field f = LinkWebhookDispatcher.class.getDeclaredField("batchQueues");
      f.setAccessible(true);
      return (ConcurrentHashMap<Long, ConcurrentLinkedQueue<Map<String, Object>>>)
          f.get(dispatcher);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private boolean callShouldDeliver(LinkWebhookEntity h, ClickRecordedEvent e) throws Exception {
    Method m =
        LinkWebhookDispatcher.class.getDeclaredMethod(
            "shouldDeliver", LinkWebhookEntity.class, ClickRecordedEvent.class);
    m.setAccessible(true);
    return (boolean) m.invoke(dispatcher, h, e);
  }

  private void invokeEnqueueBatch(long id, Map<String, Object> payload) throws Exception {
    Method m = LinkWebhookDispatcher.class.getDeclaredMethod("enqueueBatch", Long.class, Map.class);
    m.setAccessible(true);
    m.invoke(dispatcher, id, payload);
  }

  private static void writeField(Object target, String name, Object value) {
    try {
      Field f = target.getClass().getDeclaredField(name);
      f.setAccessible(true);
      f.set(target, value);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }
}
