package com.example.short_link.link.classifier.application.helper;

import java.net.URI;
import java.net.URISyntaxException;

public final class ReferrerNormalizer {

  private static final int MAX_LENGTH = 512;

  private ReferrerNormalizer() {}

  public static String normalize(String referrer) {
    if (referrer == null || referrer.isBlank()) {
      return null;
    }
    try {
      URI uri = new URI(referrer.trim());
      String host = uri.getHost();
      if (host == null) {
        return null;
      }
      String path = uri.getRawPath();
      String result = uri.getScheme() + "://" + host.toLowerCase();
      if (path != null && !path.isEmpty() && !path.equals("/")) {
        result += path;
      }
      return result.length() > MAX_LENGTH ? result.substring(0, MAX_LENGTH) : result;
    } catch (URISyntaxException e) {
      return null;
    }
  }

  public static String hostOf(String referrer) {
    if (referrer == null || referrer.isBlank()) {
      return null;
    }
    try {
      String host = new URI(referrer.trim()).getHost();
      return host == null ? null : host.toLowerCase();
    } catch (URISyntaxException e) {
      return null;
    }
  }
}
