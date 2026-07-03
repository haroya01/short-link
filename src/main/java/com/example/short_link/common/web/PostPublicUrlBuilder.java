package com.example.short_link.common.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Builds the public web URL of a blog post — {@code {frontend}/p/{username}/{slug}}. The single
 * source of truth for that shape on the server side (the client mirror lives in the SPA's postHref
 * helper). The frontend runs next-intl with {@code localePrefix: "always"}, so a locale-less path
 * redirects to the default-locale route; keeping the URL locale-agnostic lets one server value work
 * for every viewer. Returns null when the author has no claimed handle, since the public route is
 * keyed by handle.
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
