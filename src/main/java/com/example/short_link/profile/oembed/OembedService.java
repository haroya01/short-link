package com.example.short_link.profile.oembed;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
public class OembedService {

  static final String CACHE = "oembed";

  private static final ParameterizedTypeReference<Map<String, Object>> JSON_MAP =
      new ParameterizedTypeReference<>() {};

  private final RestClient restClient;

  public OembedService(RestClient oembedRestClient) {
    this.restClient = oembedRestClient;
  }

  /**
   * Returns oembed metadata for a whitelisted provider URL, or empty if the host isn't supported.
   * Cached 24h per input URL; provider 4xx/5xx are swallowed into an empty response so the frontend
   * can render the bare URL as a fallback without surfacing a transient outage.
   */
  @Cacheable(value = CACHE, key = "#url", unless = "#result == null")
  public OembedResponse fetch(String url) {
    return EmbedProvider.resolve(url)
        .map(provider -> fetchFromProvider(provider, url))
        .orElse(null);
  }

  private OembedResponse fetchFromProvider(EmbedProvider provider, String url) {
    try {
      Map<String, Object> body =
          restClient
              .get()
              .uri(
                  provider.endpoint(),
                  uriBuilder ->
                      uriBuilder.queryParam("url", url).queryParam("format", "json").build())
              .retrieve()
              .onStatus(HttpStatusCode::isError, (req, res) -> {})
              .body(JSON_MAP);
      if (body == null) return OembedResponse.empty(provider.id());
      return new OembedResponse(
          provider.id(),
          asString(body.get("type")),
          asString(body.get("title")),
          asString(body.get("author_name")),
          asString(body.get("thumbnail_url")),
          asString(body.get("html")),
          asInt(body.get("width")),
          asInt(body.get("height")));
    } catch (RuntimeException ex) {
      log.debug("oembed fetch failed [{}] {}: {}", provider.id(), url, ex.getMessage());
      return OembedResponse.empty(provider.id());
    }
  }

  private static String asString(Object v) {
    return v == null ? null : v.toString();
  }

  private static Integer asInt(Object v) {
    if (v instanceof Number n) return n.intValue();
    return null;
  }
}
