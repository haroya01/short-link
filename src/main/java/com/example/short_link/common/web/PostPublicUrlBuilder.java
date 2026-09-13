package com.example.short_link.common.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Returns a locale-free public post URL; the frontend redirects to its default locale. Returns null
 * when the author has no handle, which the public route requires.
 */
@Component
public class PostPublicUrlBuilder {

  private final String frontendBaseUrl;

  public PostPublicUrlBuilder(@Value("${short-link.frontend-base-url}") String frontendBaseUrl) {
    this.frontendBaseUrl = frontendBaseUrl;
  }

  public String build(String username, String slug) {
    if (username == null || username.isBlank() || slug == null || slug.isBlank()) {
      return null;
    }
    return frontendBaseUrl + "/p/" + username + "/" + slug;
  }
}
