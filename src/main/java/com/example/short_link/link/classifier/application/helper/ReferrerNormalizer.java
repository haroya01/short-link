package com.example.short_link.link.classifier.application.helper;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

public final class ReferrerNormalizer {

  private static final int MAX_LENGTH = 512;
  private static final int MAX_HOST_LENGTH = 255;

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
      String result = uri.getScheme() + "://" + host.toLowerCase(Locale.ROOT);
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
      if (host == null) {
        return null;
      }
      String lower = host.toLowerCase(Locale.ROOT);
      return lower.length() > MAX_HOST_LENGTH ? lower.substring(0, MAX_HOST_LENGTH) : lower;
    } catch (URISyntaxException e) {
      return null;
    }
  }
}
