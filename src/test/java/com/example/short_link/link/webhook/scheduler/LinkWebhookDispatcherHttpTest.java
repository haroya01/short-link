package com.example.short_link.link.webhook.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.example.short_link.common.net.HttpFetcher;
import com.example.short_link.common.net.PublicHttpUrlGuard;
import com.example.short_link.common.net.PublicHttpUrlGuard.Resolved;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.webhook.domain.LinkWebhookEntity;
import com.example.short_link.link.webhook.domain.WebhookFormat;
import com.example.short_link.support.TestEntities;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class LinkWebhookDispatcherHttpTest {

  private static final String URL = "https://example.com/hook";

  @Test
  void successPathRecordsAndIncrementsOkCounter() {
    Probe p = run(fetcherReturning(200));
    assertThat(p.hook.getLastStatusCode()).isEqualTo(200);
    assertThat(p.hook.getConsecutiveFailures()).isZero();
    assertThat(p.registry.counter("webhook.delivery", "result", "ok").count()).isEqualTo(1.0);
  }

  @Test
  void non2xxPathRecordsFailureAndIncrementsCounter() {
    Probe p = run(fetcherReturning(503));
    assertThat(p.hook.getConsecutiveFailures()).isEqualTo(1);
    assertThat(p.hook.getLastError()).contains("non-2xx");
    assertThat(p.registry.counter("webhook.delivery", "result", "non_2xx").count()).isEqualTo(1.0);
  }

  @Test
  void exceptionPathRecordsFailureAndIncrementsCounter() {
    Probe p = run(fetcherThrowing(new UncheckedIOException(new IOException("connect reset"))));
    assertThat(p.hook.getConsecutiveFailures()).isEqualTo(1);
    assertThat(p.hook.getLastError()).contains("UncheckedIOException");
    assertThat(p.registry.counter("webhook.delivery", "result", "exception").count())
        .isEqualTo(1.0);
  }

  @Test
  void blocksWhenUrlNotPublic() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    WebhookHttpDeliveryClient client =
        new WebhookHttpDeliveryClient(registry, mock(HttpFetcher.class));
    LinkWebhookEntity hook =
        new LinkWebhookEntity(
            new LinkId(1L), "http://localhost/hook", "secret", "n", WebhookFormat.GENERIC);
    TestEntities.withId(hook, 99L);

    client.deliver(hook, "{\"a\":1}", "click");

    assertThat(registry.counter("webhook.delivery", "result", "blocked").count()).isEqualTo(1.0);
    assertThat(hook.getLastError()).contains("public host");
  }

  @Test
  void failsToSignWhenSecretInvalid() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    WebhookHttpDeliveryClient client =
        new WebhookHttpDeliveryClient(registry, mock(HttpFetcher.class));
    LinkWebhookEntity hook =
        new LinkWebhookEntity(
            new LinkId(1L), "https://example.com/hook", null, "n", WebhookFormat.GENERIC);
    TestEntities.withId(hook, 99L);

    client.deliver(hook, "{\"a\":1}", "click");

    assertThat(registry.counter("webhook.delivery", "result", "sign_error").count()).isEqualTo(1.0);
    assertThat(hook.getLastError()).contains("signature failed");
  }

  private record Probe(LinkWebhookEntity hook, SimpleMeterRegistry registry) {}

  private static Probe run(HttpFetcher fetcher) {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    WebhookHttpDeliveryClient client = new WebhookHttpDeliveryClient(registry, fetcher);
    LinkWebhookEntity hook =
        new LinkWebhookEntity(new LinkId(1L), URL, "secret", "test", WebhookFormat.GENERIC);
    TestEntities.withId(hook, 99L);
    try (MockedStatic<PublicHttpUrlGuard> guard = mockStatic(PublicHttpUrlGuard.class)) {
      Resolved resolved = new Resolved(URI.create(URL), List.<InetAddress>of());
      guard.when(() -> PublicHttpUrlGuard.resolve(URL)).thenReturn(Optional.of(resolved));
      client.deliver(hook, "{\"a\":1}", "click");
    }
    return new Probe(hook, registry);
  }

  private static HttpFetcher fetcherReturning(int status) {
    HttpFetcher fetcher = mock(HttpFetcher.class);
    when(fetcher.fetch(any(HttpFetcher.Request.class)))
        .thenReturn(new HttpFetcher.Response(status, Map.of(), new byte[0]));
    return fetcher;
  }

  private static HttpFetcher fetcherThrowing(RuntimeException thrown) {
    HttpFetcher fetcher = mock(HttpFetcher.class);
    when(fetcher.fetch(any(HttpFetcher.Request.class))).thenThrow(thrown);
    return fetcher;
  }
}
