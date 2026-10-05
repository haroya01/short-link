package com.example.short_link.federation.application;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "short-link.federation")
public record FederationProperties(String baseUrl, String profileBaseUrl) {

  public FederationProperties {
    baseUrl = trimSlash(baseUrl == null || baseUrl.isBlank() ? "http://localhost:8080" : baseUrl);
    profileBaseUrl =
        trimSlash(
            profileBaseUrl == null || profileBaseUrl.isBlank()
                ? "https://blog.kurl.me"
                : profileBaseUrl);
  }

  public String domain() {
    URI uri = URI.create(baseUrl);
    return uri.getPort() == -1 ? uri.getHost() : uri.getHost() + ":" + uri.getPort();
  }

  private static String trimSlash(String url) {
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }
}
