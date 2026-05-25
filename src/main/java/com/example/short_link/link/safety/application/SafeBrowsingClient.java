package com.example.short_link.link.safety.application;

import static com.example.short_link.common.config.SafeBrowsingConfig.SAFE_BROWSING_CB;

import com.example.short_link.common.config.SafeBrowsingProperties;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.List;
import java.util.Map;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class SafeBrowsingClient {

  static final String CACHE_NAME = "safebrowsing";

  private static final List<String> THREAT_TYPES =
      List.of(
          "MALWARE", "SOCIAL_ENGINEERING", "UNWANTED_SOFTWARE", "POTENTIALLY_HARMFUL_APPLICATION");

  private static final ParameterizedTypeReference<Map<String, Object>> RESPONSE_TYPE =
      new ParameterizedTypeReference<>() {};

  private final RestClient restClient;
  private final SafeBrowsingProperties properties;
  private final CircuitBreaker circuitBreaker;

  public SafeBrowsingClient(
      RestClient safeBrowsingRestClient,
      SafeBrowsingProperties properties,
      CircuitBreakerRegistry circuitBreakerRegistry) {
    this.restClient = safeBrowsingRestClient;
    this.properties = properties;
    this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(SAFE_BROWSING_CB);
  }

  /**
   * Returns true when the URL is known-safe (or unknown) per Google SafeBrowsing. Cached per {@code
   * cacheKey}; cache hits skip both the HTTP call and the breaker entirely. Cache misses route
   * through the breaker — when it's open the call short-circuits with {@link
   * CallNotPermittedException}, which we map to {@code true} (allow-through, same as a network
   * failure) so a SafeBrowsing outage doesn't take shortening down with it.
   */
  @Cacheable(value = CACHE_NAME, key = "#cacheKey")
  public boolean isSafeForKey(String cacheKey, String fullUrl) {
    try {
      return circuitBreaker.executeSupplier(() -> callSafeBrowsing(fullUrl));
    } catch (CallNotPermittedException open) {
      // Breaker is OPEN — fail-open so users can still shorten URLs while SafeBrowsing recovers.
      return true;
    }
  }

  private boolean callSafeBrowsing(String fullUrl) {
    Map<String, Object> body =
        Map.of(
            "client", Map.of("clientId", "short-link", "clientVersion", "1.0"),
            "threatInfo",
                Map.of(
                    "threatTypes", THREAT_TYPES,
                    "platformTypes", List.of("ANY_PLATFORM"),
                    "threatEntryTypes", List.of("URL"),
                    "threatEntries", List.of(Map.of("url", fullUrl))));

    Map<String, Object> response =
        restClient
            .post()
            .uri(
                uriBuilder ->
                    uriBuilder
                        .path("/v4/threatMatches:find")
                        .queryParam("key", properties.apiKey())
                        .build())
            .body(body)
            .retrieve()
            .body(RESPONSE_TYPE);

    return response == null || response.isEmpty() || response.get("matches") == null;
  }
}
