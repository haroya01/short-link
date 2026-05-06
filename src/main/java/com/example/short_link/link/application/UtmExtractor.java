package com.example.short_link.link.application;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class UtmExtractor {

  private UtmExtractor() {}

  public static UtmParams extract(String url) {
    if (url == null) {
      return UtmParams.empty();
    }
    int q = url.indexOf('?');
    if (q == -1 || q == url.length() - 1) {
      return UtmParams.empty();
    }
    Map<String, String> params = parseQuery(url.substring(q + 1));
    return new UtmParams(
        params.get("utm_source"),
        params.get("utm_medium"),
        params.get("utm_campaign"),
        params.get("utm_term"),
        params.get("utm_content"));
  }

  private static Map<String, String> parseQuery(String query) {
    Map<String, String> result = new HashMap<>();
    int fragment = query.indexOf('#');
    String body = fragment == -1 ? query : query.substring(0, fragment);
    for (String pair : body.split("&")) {
      if (pair.isEmpty()) {
        continue;
      }
      int eq = pair.indexOf('=');
      if (eq == -1) {
        continue;
      }
      String key = pair.substring(0, eq);
      String value = pair.substring(eq + 1);
      try {
        result.put(
            URLDecoder.decode(key, StandardCharsets.UTF_8),
            URLDecoder.decode(value, StandardCharsets.UTF_8));
      } catch (IllegalArgumentException ignored) {
      }
    }
    return result;
  }
}
