package com.example.short_link.link.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.link.domain.LinkWebhookEntity;
import com.example.short_link.link.domain.LinkWebhookRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

class LinkWebhookDispatcherHttpTest {

  private LinkWebhookRepository repository;
  private SimpleMeterRegistry meterRegistry;
  private LinkWebhookDispatcher dispatcher;
  private HttpClient httpClient;

  @BeforeEach
  void setUp() throws Exception {
    repository = mock(LinkWebhookRepository.class);
    meterRegistry = new SimpleMeterRegistry();
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    dispatcher =
        new LinkWebhookDispatcher(repository, JsonMapper.builder().build(), meterRegistry, redis);
    httpClient = mock(HttpClient.class);
    Field f = LinkWebhookDispatcher.class.getDeclaredField("httpClient");
    f.setAccessible(true);
    f.set(dispatcher, httpClient);
  }

  private LinkWebhookEntity newHook() {
    LinkWebhookEntity h =
        new LinkWebhookEntity(
            1L, "https://example.com/hook", "secret", "name", WebhookFormat.GENERIC);
    writeField(h, "id", 7L);
    return h;
  }

  private static void writeField(Object target, String name, Object value) {
    try {
      Field f = target.getClass().getDeclaredField(name);
      f.setAccessible(true);
      f.set(target, value);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void deliver2xxRecordsSuccessAndOkCounter() throws Exception {
    LinkWebhookEntity hook = newHook();
    HttpResponse<Void> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(200);
    Mockito.<HttpResponse<Void>>when(httpClient.send(any(), any())).thenReturn(response);

    dispatcher.deliver(hook, "{\"x\":1}", "click");

    assertThat(meterRegistry.counter("webhook.delivery", "result", "ok").count()).isEqualTo(1.0);
  }

  @Test
  @SuppressWarnings("unchecked")
  void deliverNon2xxRecordsFailureAndNon2xxCounter() throws Exception {
    LinkWebhookEntity hook = newHook();
    HttpResponse<Void> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(503);
    Mockito.<HttpResponse<Void>>when(httpClient.send(any(), any())).thenReturn(response);

    dispatcher.deliver(hook, "{\"x\":1}", "click");

    assertThat(meterRegistry.counter("webhook.delivery", "result", "non_2xx").count())
        .isEqualTo(1.0);
  }

  @Test
  @SuppressWarnings("unchecked")
  void deliverNetworkExceptionRecordsExceptionCounter() throws Exception {
    LinkWebhookEntity hook = newHook();
    when(httpClient.send(any(), any())).thenThrow(new IOException("connection refused"));

    dispatcher.deliver(hook, "{\"x\":1}", "click");

    assertThat(meterRegistry.counter("webhook.delivery", "result", "exception").count())
        .isEqualTo(1.0);
  }

  @Test
  @SuppressWarnings("unchecked")
  void deliverDiscordFormatSkipsSignatureHeader() throws Exception {
    LinkWebhookEntity hook =
        new LinkWebhookEntity(
            1L, "https://discord.com/api/webhooks/x", "secret", "name", WebhookFormat.DISCORD);
    writeField(hook, "id", 8L);
    HttpResponse<Void> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(200);
    Mockito.<HttpResponse<Void>>when(httpClient.send(any(), any())).thenReturn(response);

    dispatcher.deliver(hook, "{\"content\":\"hi\"}", "click");

    // 정상 흐름, 200 → ok counter
    assertThat(meterRegistry.counter("webhook.delivery", "result", "ok").count()).isEqualTo(1.0);
    verify(httpClient).send(any(), any());
  }
}
