package com.example.short_link.link.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.config.SafeBrowsingProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.HttpClientErrorException;

@ExtendWith(MockitoExtension.class)
class UrlSafetyCheckerTest {

  @Mock SafeBrowsingClient client;

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

  private UrlSafetyChecker checker(boolean enabled, String apiKey) {
    return new UrlSafetyChecker(
        client,
        new SafeBrowsingProperties(enabled, apiKey, Duration.ofHours(1), Duration.ofSeconds(2)),
        registry);
  }

  @Test
  void allowsAllWhenDisabled() {
    UrlSafetyChecker c = checker(false, "key");

    assertThat(c.isSafe("https://anything.example")).isTrue();
  }

  @Test
  void allowsAllWhenApiKeyMissing() {
    UrlSafetyChecker c = checker(true, "");

    assertThat(c.isSafe("https://anything.example")).isTrue();
  }

  @Test
  void allowsNullUrlWithoutCallingClient() {
    UrlSafetyChecker c = checker(true, "key");

    assertThat(c.isSafe(null)).isTrue();
    assertThat(c.isSafe("   ")).isTrue();
  }

  @Test
  void allowsUnnormalizableUrl() {
    UrlSafetyChecker c = checker(true, "key");

    assertThat(c.isSafe("not a url")).isTrue();
  }

  @Test
  void delegatesSafeResultAndCountsMetric() {
    UrlSafetyChecker c = checker(true, "key");
    when(client.isSafeForKey(any(), any())).thenReturn(true);

    boolean result = c.isSafe("https://safe.example/page?utm=x");

    assertThat(result).isTrue();
    assertThat(registry.counter("safe_browsing.check", "result", "safe").count()).isEqualTo(1.0);
    verify(client)
        .isSafeForKey(eq("https://safe.example/page"), eq("https://safe.example/page?utm=x"));
  }

  @Test
  void delegatesMaliciousResultAndCountsMetric() {
    UrlSafetyChecker c = checker(true, "key");
    when(client.isSafeForKey(any(), any())).thenReturn(false);

    boolean result = c.isSafe("https://bad.example");

    assertThat(result).isFalse();
    assertThat(registry.counter("safe_browsing.check", "result", "malicious").count())
        .isEqualTo(1.0);
  }

  @Test
  void failsOpenOnGenericError() {
    UrlSafetyChecker c = checker(true, "key");
    when(client.isSafeForKey(any(), any())).thenThrow(new RuntimeException("network down"));

    assertThat(c.isSafe("https://timeout.example")).isTrue();
    assertThat(registry.counter("safe_browsing.check", "result", "error").count()).isEqualTo(1.0);
  }

  @Test
  void failsOpenWithDistinctMetricOnAuthError() {
    UrlSafetyChecker c = checker(true, "key");
    when(client.isSafeForKey(any(), any())).thenThrow(HttpClientErrorException.Unauthorized.class);

    assertThat(c.isSafe("https://anywhere.example")).isTrue();
    assertThat(registry.counter("safe_browsing.check", "result", "auth_error").count())
        .isEqualTo(1.0);
  }
}
