package com.example.short_link.common.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class SafeBrowsingConfig {

  static final String CACHE_NAME = "safebrowsing";

  public static final String SAFE_BROWSING_CB = "safe-browsing";

  @Bean
  public RestClient safeBrowsingRestClient(SafeBrowsingProperties properties) {
    Duration connect = Duration.ofSeconds(2);
    Duration read = properties.timeout() == null ? Duration.ofSeconds(2) : properties.timeout();
    HttpClient http = HttpClient.newBuilder().connectTimeout(connect).build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
    factory.setReadTimeout(read);
    return RestClient.builder()
        .requestFactory(factory)
        .baseUrl("https://safebrowsing.googleapis.com")
        .build();
  }

  @Bean
  public RedisCacheManagerBuilderCustomizer safeBrowsingCacheCustomizer(
      SafeBrowsingProperties properties) {
    Duration ttl = properties.cacheTtl() == null ? Duration.ofHours(1) : properties.cacheTtl();
    return builder ->
        builder.withCacheConfiguration(
            CACHE_NAME, RedisCacheConfiguration.defaultCacheConfig().entryTtl(ttl));
  }

  /**
   * Opens after a short failure burst so an unavailable or quota-exhausted API does not impose the
   * full 2s read timeout on every shorten call. Re-probes after 30s.
   */
  @Bean
  public CircuitBreakerRegistry circuitBreakerRegistry(MeterRegistry meterRegistry) {
    CircuitBreakerConfig safeBrowsing =
        CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(20)
            .minimumNumberOfCalls(10)
            .failureRateThreshold(50f)
            .slowCallDurationThreshold(Duration.ofSeconds(3))
            .slowCallRateThreshold(80f)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .permittedNumberOfCallsInHalfOpenState(3)
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .build();
    CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(safeBrowsing);
    // Register up-front so it shows in metrics even before first call (state defaults to CLOSED).
    CircuitBreaker.of(SAFE_BROWSING_CB, safeBrowsing);
    registry.circuitBreaker(SAFE_BROWSING_CB, safeBrowsing);
    TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry).bindTo(meterRegistry);
    return registry;
  }
}
