package com.example.short_link.profile.application.oembed;

import com.example.short_link.profile.exception.ProfileErrorCode;
import com.example.short_link.profile.exception.ProfileException;
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
   * Returns oembed metadata for a whitelisted provider URL. Throws {@link ProfileException} when
   * the host isn't a registered provider — the controller maps that to 422 via {@code
   * ProfileExceptionHandler}. Cached 24h per input URL; provider 4xx/5xx are swallowed into an
   * empty response so the frontend can render the bare URL as a fallback without surfacing a
   * transient outage.
   */
  @Cacheable(value = CACHE, key = "#url")
  public OembedMetadata fetch(String url) {
    EmbedProvider provider =
        EmbedProvider.resolve(url)
            .orElseThrow(() -> new ProfileException(ProfileErrorCode.OEMBED_NOT_APPLICABLE));
    return fetchFromProvider(provider, url);
  }

  private OembedMetadata fetchFromProvider(EmbedProvider provider, String url) {
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
      if (body == null) return OembedMetadata.empty(provider.id());
      return new OembedMetadata(
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
      return OembedMetadata.empty(provider.id());
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
