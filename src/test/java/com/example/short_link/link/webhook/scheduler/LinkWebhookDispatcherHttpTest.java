package com.example.short_link.link.webhook.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.example.short_link.common.net.PublicHttpUrlGuard;
import com.example.short_link.common.net.PublicHttpUrlGuard.Resolved;
import com.example.short_link.link.webhook.domain.LinkWebhookEntity;
import com.example.short_link.link.webhook.domain.WebhookFormat;
import com.example.short_link.link.webhook.domain.repository.LinkWebhookRepository;
import com.example.short_link.support.TestEntities;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

class LinkWebhookDispatcherHttpTest {

  private static final String URL = "https://example.com/hook";

  @Test
  void successPathRecordsAndIncrementsOkCounter() {
    Probe p = run(stubResponding(200));
    assertThat(p.hook.getLastStatusCode()).isEqualTo(200);
    assertThat(p.hook.getConsecutiveFailures()).isZero();
    assertThat(p.registry.counter("webhook.delivery", "result", "ok").count()).isEqualTo(1.0);
  }

  @Test
  void non2xxPathRecordsFailureAndIncrementsCounter() {
    Probe p = run(stubResponding(503));
    assertThat(p.hook.getConsecutiveFailures()).isEqualTo(1);
    assertThat(p.hook.getLastError()).contains("non-2xx");
    assertThat(p.registry.counter("webhook.delivery", "result", "non_2xx").count()).isEqualTo(1.0);
  }

  @Test
  void exceptionPathRecordsFailureAndIncrementsCounter() {
    Probe p = run(stubThrowing(new IOException("connect reset")));
    assertThat(p.hook.getConsecutiveFailures()).isEqualTo(1);
    assertThat(p.hook.getLastError()).contains("IOException");
    assertThat(p.registry.counter("webhook.delivery", "result", "exception").count())
        .isEqualTo(1.0);
  }

  private record Probe(LinkWebhookEntity hook, SimpleMeterRegistry registry) {}

  private static Probe run(CloseableHttpClient client) {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    LinkWebhookDispatcher dispatcher =
        new LinkWebhookDispatcher(
            mock(LinkWebhookRepository.class),
            JsonMapper.builder().build(),
            registry,
            mock(StringRedisTemplate.class)) {
          @Override
          protected CloseableHttpClient buildPinnedClient(Resolved resolved) {
            return client;
          }
        };
    LinkWebhookEntity hook =
        new LinkWebhookEntity(1L, URL, "secret", "test", WebhookFormat.GENERIC);
    TestEntities.withId(hook, 99L);
    try (MockedStatic<PublicHttpUrlGuard> guard = mockStatic(PublicHttpUrlGuard.class)) {
      Resolved resolved = new Resolved(URI.create(URL), List.<InetAddress>of());
      guard.when(() -> PublicHttpUrlGuard.resolve(URL)).thenReturn(Optional.of(resolved));
      dispatcher.deliver(hook, "{\"a\":1}", "click");
    }
    return new Probe(hook, registry);
  }

  private static CloseableHttpClient stubResponding(int status) {
    CloseableHttpClient client = mock(CloseableHttpClient.class);
    try {
      when(client.execute(any(ClassicHttpRequest.class), any(HttpClientResponseHandler.class)))
          .thenAnswer(
              inv -> {
                HttpClientResponseHandler<?> handler = inv.getArgument(1);
                return handler.handleResponse(new BasicClassicHttpResponse(status));
              });
    } catch (IOException e) {
      throw new AssertionError(e);
    }
    return client;
  }

  private static CloseableHttpClient stubThrowing(IOException thrown) {
    CloseableHttpClient client = mock(CloseableHttpClient.class);
    try {
      when(client.execute(any(ClassicHttpRequest.class), any(HttpClientResponseHandler.class)))
          .thenThrow(thrown);
    } catch (IOException e) {
      throw new AssertionError(e);
    }
    return client;
  }
}
