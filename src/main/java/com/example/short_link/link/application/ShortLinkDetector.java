package com.example.short_link.link.application;

import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Recognizes whether a URL is already one of our own short links (same host as {@link
 * ShortLinkUrlBuilder} produces) and extracts its short code. Used to avoid double-wrapping when an
 * author pastes an existing kurl link as a CTA target.
 */
@Component
public class ShortLinkDetector {

  private static final Pattern CODE = Pattern.compile("^[0-9A-Za-z]{3,16}$");

  private final String prefix;

  public ShortLinkDetector(@Value("${short-link.base-url}") String baseUrl) {
    this.prefix = baseUrl.replaceAll("/+$", "") + "/";
  }

  public boolean isShortLink(String url) {
    return extractCode(url) != null;
  }

  /** Returns the short code if {@code url} is one of our short links, else {@code null}. */
  public String extractCode(String url) {
    if (url == null || !url.startsWith(prefix)) {
      return null;
    }
    String rest = url.substring(prefix.length());
    int cut = rest.indexOf('?');
    if (cut >= 0) rest = rest.substring(0, cut);
    cut = rest.indexOf('#');
    if (cut >= 0) rest = rest.substring(0, cut);
    if (rest.endsWith("/")) rest = rest.substring(0, rest.length() - 1);
    return CODE.matcher(rest).matches() ? rest : null;
  }
}
