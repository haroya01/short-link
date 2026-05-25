package com.example.short_link.link.application.helper;

import com.example.short_link.link.application.dto.UtmParams;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.springframework.web.util.UriComponentsBuilder;

public final class UtmExtractor {

  private UtmExtractor() {}

  public static UtmParams extract(String url) {
    if (url == null || url.isBlank()) {
      return UtmParams.empty();
    }
    try {
      var params = UriComponentsBuilder.fromUriString(url).build().getQueryParams();
      return new UtmParams(
          decode(params.getFirst("utm_source")),
          decode(params.getFirst("utm_medium")),
          decode(params.getFirst("utm_campaign")),
          decode(params.getFirst("utm_term")),
          decode(params.getFirst("utm_content")));
    } catch (IllegalArgumentException e) {
      return UtmParams.empty();
    }
  }

  private static String decode(String value) {
    if (value == null) {
      return null;
    }
    try {
      return URLDecoder.decode(value, StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
