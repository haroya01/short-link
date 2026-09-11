package com.example.short_link.link.safety.infrastructure;

import static com.example.short_link.common.config.SafeBrowsingConfig.SAFE_BROWSING_CB;

import com.example.short_link.common.config.SafeBrowsingProperties;
import com.example.short_link.link.safety.application.UrlThreatLookup;
import com.example.short_link.link.safety.application.UrlThreatLookupException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.List;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/** Google Safe Browsing v4 transport, outage protection, and failure translation. */
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
      // Preserve the existing outage policy: an open circuit yields a cacheable safe result.
      return true;
    } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden failure) {
      throw UrlThreatLookupException.authenticationFailure(failure);
    } catch (Exception failure) {
      throw UrlThreatLookupException.unavailable(failure);
    }
  }

  private boolean requestVerdict(String fullUrl) {
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
