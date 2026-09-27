package com.example.short_link.link.safety.application;

import static com.example.short_link.common.config.SafeBrowsingConfig.SAFE_BROWSING_CB;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.short_link.common.config.SafeBrowsingProperties;
import com.example.short_link.link.safety.infrastructure.GoogleSafeBrowsingLookup;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SafeBrowsingCachePolicyTest {

  private static final SafeBrowsingProperties PROPERTIES =
      new SafeBrowsingProperties(true, "key", Duration.ofHours(1), Duration.ofSeconds(2));

  @Test
  void openBreakerAllowsAndCachesBooleanWhichRemainsSafeAfterRecovery() {
    try (var context = new AnnotationConfigApplicationContext(CacheConfiguration.class)) {
      var registry = context.getBean(CircuitBreakerRegistry.class);
      var breaker = registry.circuitBreaker(SAFE_BROWSING_CB);
      var server = context.getBean(MockRestServiceServer.class);
      var client = context.getBean(SafeBrowsingClient.class);
      var metrics = new SimpleMeterRegistry();
      var checker = new UrlSafetyChecker(client, PROPERTIES, metrics);
      breaker.transitionToOpenState();

      assertThat(checker.isSafe("https://example.com/path")).isTrue();
      assertThat(
              context
                  .getBean(CacheManager.class)
                  .getCache("safebrowsing")
                  .get("https://example.com/path", Boolean.class))
          .isTrue();
      breaker.transitionToClosedState();
      assertThat(checker.isSafe("https://example.com/path")).isTrue();
      server.verify();
      assertThat(breaker.getMetrics().getNumberOfBufferedCalls()).isZero();
      assertThat(metrics.counter("safe_browsing.check", "result", "safe").count()).isEqualTo(2);

      server
          .expect(method(HttpMethod.POST))
          .andRespond(withSuccess("{\"matches\":[]}", MediaType.APPLICATION_JSON));
      assertThat(checker.isSafe("https://example.com/other")).isFalse();
      assertThat(metrics.counter("safe_browsing.check", "result", "malicious").count())
          .isEqualTo(1);
      server.verify();
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void normalizedKeysCacheBooleanVerdictsButTheProviderReceivesTheFullUrl(boolean safe) {
    try (var context = new AnnotationConfigApplicationContext(CacheConfiguration.class)) {
      var server = context.getBean(MockRestServiceServer.class);
      String response = safe ? "{}" : "{\"matches\":[]}";
      server
          .expect(method(HttpMethod.POST))
          .andExpect(
              content()
                  .json(
                      """
                      {"threatInfo":{"threatEntries":[{"url":"https://example.com/path?utm=one"}]}}
                      """))
          .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
      var metrics = new SimpleMeterRegistry();
      var checker =
          new UrlSafetyChecker(context.getBean(SafeBrowsingClient.class), PROPERTIES, metrics);

      assertThat(checker.isSafe("https://example.com/path?utm=one")).isEqualTo(safe);
      assertThat(checker.isSafe("https://example.com/path?utm=two")).isEqualTo(safe);
      assertThat(
              context
                  .getBean(CacheManager.class)
                  .getCache("safebrowsing")
                  .get("https://example.com/path", Boolean.class))
          .isEqualTo(safe);
      assertThat(
              metrics.counter("safe_browsing.check", "result", safe ? "safe" : "malicious").count())
          .isEqualTo(2);
      assertThat(
              context
                  .getBean(CircuitBreakerRegistry.class)
                  .circuitBreaker(SAFE_BROWSING_CB)
                  .getMetrics()
                  .getNumberOfSuccessfulCalls())
          .isEqualTo(1);
      server.verify();
    }
  }

  @ParameterizedTest
  @CsvSource({"401, auth_error", "403, auth_error", "500, error"})
  void failedLookupsAllowWithoutCachingAndKeepTheirMetricClassification(
      int status, String metricResult) {
    try (var context = new AnnotationConfigApplicationContext(CacheConfiguration.class)) {
      var server = context.getBean(MockRestServiceServer.class);
      server
          .expect(ExpectedCount.times(2), method(HttpMethod.POST))
          .andRespond(withStatus(HttpStatus.valueOf(status)));
      var metrics = new SimpleMeterRegistry();
      var checker =
          new UrlSafetyChecker(context.getBean(SafeBrowsingClient.class), PROPERTIES, metrics);

      assertThat(checker.isSafe("https://example.com/path")).isTrue();
      assertThat(checker.isSafe("https://example.com/path")).isTrue();
      assertThat(
              context
                  .getBean(CacheManager.class)
                  .getCache("safebrowsing")
                  .get("https://example.com/path"))
          .isNull();
      assertThat(metrics.counter("safe_browsing.check", "result", metricResult).count())
          .isEqualTo(2);
      assertThat(metrics.find("safe_browsing.check").tag("result", "safe").counter()).isNull();
      assertThat(
              context
                  .getBean(CircuitBreakerRegistry.class)
                  .circuitBreaker(SAFE_BROWSING_CB)
                  .getMetrics()
                  .getNumberOfFailedCalls())
          .isEqualTo(2);
      server.verify();
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableCaching
  static class CacheConfiguration {

    private final RestClient.Builder builder =
        RestClient.builder().baseUrl("https://safebrowsing.googleapis.com");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    @Bean
    MockRestServiceServer providerServer() {
      return server;
    }

    @Bean
    CacheManager cacheManager() {
      return new ConcurrentMapCacheManager("safebrowsing");
    }

    @Bean
    CircuitBreakerRegistry circuitBreakerRegistry() {
      return CircuitBreakerRegistry.ofDefaults();
    }

    @Bean
    UrlThreatLookup lookup(CircuitBreakerRegistry registry) {
      return new GoogleSafeBrowsingLookup(builder.build(), PROPERTIES, registry);
    }

    @Bean
    SafeBrowsingClient client(UrlThreatLookup lookup) {
      return new SafeBrowsingClient(lookup);
    }
  }
}
