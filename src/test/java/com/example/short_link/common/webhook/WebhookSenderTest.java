package com.example.short_link.common.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.example.short_link.common.net.HttpFetcher;
import com.example.short_link.common.net.PublicHttpUrlGuard;
import com.example.short_link.common.net.PublicHttpUrlGuard.Resolved;
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

class WebhookSenderTest {

  private static final String URL = "https://example.com/hook";

  private static WebhookSender.Result send(
      HttpFetcher fetcher, boolean sign, String secret, SimpleMeterRegistry registry) {
    try (MockedStatic<PublicHttpUrlGuard> guard = mockStatic(PublicHttpUrlGuard.class)) {
      Resolved resolved = new Resolved(URI.create(URL), List.<InetAddress>of());
      guard.when(() -> PublicHttpUrlGuard.resolve(URL)).thenReturn(Optional.of(resolved));
      return WebhookSender.send(fetcher, registry, URL, secret, sign, "{\"a\":1}", "like");
    }
  }

  private static HttpFetcher returning(int status) {
    HttpFetcher fetcher = mock(HttpFetcher.class);
    when(fetcher.fetch(any(HttpFetcher.Request.class)))
        .thenReturn(new HttpFetcher.Response(status, Map.of(), new byte[0]));
    return fetcher;
  }

  @Test
  void signedSuccess() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    WebhookSender.Result r = send(returning(200), true, "secret", registry);
    assertThat(r.ok()).isTrue();
    assertThat(r.statusCode()).isEqualTo(200);
    assertThat(registry.counter("webhook.delivery", "result", "ok").count()).isEqualTo(1.0);
  }

  @Test
  void unsignedSuccessSkipsSignature() {
    // Discord/Slack path: sign=false. Exercises the no-signature branch.
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    WebhookSender.Result r = send(returning(204), false, "secret", registry);
    assertThat(r.ok()).isTrue();
    assertThat(r.statusCode()).isEqualTo(204);
  }

  @Test
  void non2xxIsFailure() {
    WebhookSender.Result r = send(returning(503), false, "secret", new SimpleMeterRegistry());
    assertThat(r.outcome()).isEqualTo(WebhookSender.Outcome.NON_2XX);
    assertThat(r.ok()).isFalse();
    assertThat(r.statusCode()).isEqualTo(503);
    assertThat(r.error()).contains("non-2xx");
  }

  @Test
  void exceptionIsFailure() {
    HttpFetcher fetcher = mock(HttpFetcher.class);
    when(fetcher.fetch(any(HttpFetcher.Request.class)))
        .thenThrow(new UncheckedIOException(new IOException("reset")));
    WebhookSender.Result r = send(fetcher, true, "secret", new SimpleMeterRegistry());
    assertThat(r.outcome()).isEqualTo(WebhookSender.Outcome.EXCEPTION);
    assertThat(r.statusCode()).isNull();
    assertThat(r.error()).contains("UncheckedIOException");
  }

  @Test
  void blockedWhenUrlNotPublic() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    WebhookSender.Result r =
        WebhookSender.send(
            mock(HttpFetcher.class), registry, "http://localhost/x", "s", true, "{}", "like");
    assertThat(r.outcome()).isEqualTo(WebhookSender.Outcome.BLOCKED);
    assertThat(r.error()).contains("public host");
    assertThat(registry.counter("webhook.delivery", "result", "blocked").count()).isEqualTo(1.0);
  }

  @Test
  void signErrorWhenSecretInvalid() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    WebhookSender.Result r = send(returning(200), true, null, registry);
    assertThat(r.outcome()).isEqualTo(WebhookSender.Outcome.SIGN_ERROR);
    assertThat(r.error()).contains("signature failed");
    assertThat(registry.counter("webhook.delivery", "result", "sign_error").count()).isEqualTo(1.0);
  }
}
