package com.example.short_link.link.safety.infrastructure;

import static com.example.short_link.common.config.SafeBrowsingConfig.SAFE_BROWSING_CB;

import com.example.short_link.common.config.SafeBrowsingProperties;
import com.example.short_link.link.safety.application.UrlThreatLookup;
import com.example.short_link.link.safety.application.UrlThreatLookupException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Component
public class GoogleSafeBrowsingLookup implements UrlThreatLookup {
  private static final List<String> THREAT_TYPES =
      List.of(
          "MALWARE", "SOCIAL_ENGINEERING", "UNWANTED_SOFTWARE", "POTENTIALLY_HARMFUL_APPLICATION");

  private static final ParameterizedTypeReference<Map<String, Object>> RESPONSE_TYPE =
      new ParameterizedTypeReference<>() {};

  private final RestClient restClient;
  private final SafeBrowsingProperties properties;
  private final CircuitBreaker circuitBreaker;

  public GoogleSafeBrowsingLookup(
      RestClient safeBrowsingRestClient,
      SafeBrowsingProperties properties,
      CircuitBreakerRegistry circuitBreakerRegistry) {
    this.restClient = safeBrowsingRestClient;
    this.properties = properties;
    this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(SAFE_BROWSING_CB);
  }

  @Override
  public boolean isSafe(String fullUrl) {
    try {
      return circuitBreaker.executeSupplier(() -> requestVerdict(fullUrl));
    } catch (CallNotPermittedException open) {
      // An open circuit intentionally yields a cacheable allow-through result.
      return true;
    } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden failure) {
      throw UrlThreatLookupException.authenticationFailure(failure);
    } catch (Exception failure) {
      throw UrlThreatLookupException.unavailable(failure);
    }
  }

  @Override
  public Set<String> unsafeAmong(List<String> urls) {
    if (urls.isEmpty()) {
      return Set.of();
    }
    try {
      return circuitBreaker.executeSupplier(() -> matchedUrls(urls));
    } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden failure) {
      throw UrlThreatLookupException.authenticationFailure(failure);
    } catch (Exception failure) {
      throw UrlThreatLookupException.unavailable(failure);
    }
  }

  private boolean requestVerdict(String fullUrl) {
    Map<String, Object> response = request(List.of(fullUrl));
    return response == null || response.isEmpty() || response.get("matches") == null;
  }

  private Set<String> matchedUrls(List<String> urls) {
    Map<String, Object> response = request(urls);
    if (response == null || !(response.get("matches") instanceof List<?> matches)) {
      return Set.of();
    }
    Set<String> unsafe = new HashSet<>();
    for (Object match : matches) {
      if (match instanceof Map<?, ?> m
          && m.get("threat") instanceof Map<?, ?> threat
          && threat.get("url") instanceof String url) {
        unsafe.add(url);
      }
    }
    return unsafe;
  }

  private Map<String, Object> request(List<String> urls) {
    Map<String, Object> body =
        Map.of(
            "client", Map.of("clientId", "short-link", "clientVersion", "1.0"),
            "threatInfo",
                Map.of(
                    "threatTypes", THREAT_TYPES,
                    "platformTypes", List.of("ANY_PLATFORM"),
                    "threatEntryTypes", List.of("URL"),
                    "threatEntries", urls.stream().map(url -> Map.of("url", url)).toList()));

    return restClient
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
  }
}
