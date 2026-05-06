package com.example.short_link.link.application;

import com.example.short_link.common.config.SafeBrowsingConfig;
import com.example.short_link.common.config.SafeBrowsingProperties;
import java.util.List;
import java.util.Map;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class SafeBrowsingClient {

  private static final List<String> THREAT_TYPES =
      List.of(
          "MALWARE", "SOCIAL_ENGINEERING", "UNWANTED_SOFTWARE", "POTENTIALLY_HARMFUL_APPLICATION");

  private final RestClient restClient;
  private final SafeBrowsingProperties properties;

  public SafeBrowsingClient(RestClient safeBrowsingRestClient, SafeBrowsingProperties properties) {
    this.restClient = safeBrowsingRestClient;
    this.properties = properties;
  }

  @Cacheable(value = SafeBrowsingConfig.CACHE_NAME, key = "#url")
  public boolean isSafe(String url) {
    Map<String, Object> body =
        Map.of(
            "client", Map.of("clientId", "short-link", "clientVersion", "1.0"),
            "threatInfo",
                Map.of(
                    "threatTypes", THREAT_TYPES,
                    "platformTypes", List.of("ANY_PLATFORM"),
                    "threatEntryTypes", List.of("URL"),
                    "threatEntries", List.of(Map.of("url", url))));

    Map<?, ?> response =
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
            .body(Map.class);

    return response == null || response.isEmpty() || response.get("matches") == null;
  }
}
