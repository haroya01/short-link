package com.example.short_link.link.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.short_link.common.config.SafeBrowsingProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
  void delegatesSafeResultAndCountsMetric() {
    UrlSafetyChecker c = checker(true, "key");
    when(client.isSafe("https://safe.example")).thenReturn(true);

    boolean result = c.isSafe("https://safe.example");

    assertThat(result).isTrue();
    assertThat(registry.counter("safe_browsing.check", "result", "safe").count()).isEqualTo(1.0);
  }

  @Test
  void delegatesMaliciousResultAndCountsMetric() {
    UrlSafetyChecker c = checker(true, "key");
    when(client.isSafe("https://bad.example")).thenReturn(false);

    boolean result = c.isSafe("https://bad.example");

    assertThat(result).isFalse();
    assertThat(registry.counter("safe_browsing.check", "result", "malicious").count())
        .isEqualTo(1.0);
  }

  @Test
  void failsOpenWhenClientThrows() {
    UrlSafetyChecker c = checker(true, "key");
    when(client.isSafe("https://timeout.example")).thenThrow(new RuntimeException("network down"));

    boolean result = c.isSafe("https://timeout.example");

    assertThat(result).isTrue();
    assertThat(registry.counter("safe_browsing.check", "result", "error").count()).isEqualTo(1.0);
  }
}
