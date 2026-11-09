package com.example.short_link.link.domain;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;

public final class WebAddress {

  public static final int MAX_LENGTH = 2048;

  private WebAddress() {}

  public static boolean is(String url) {
    return url != null
        && (url.regionMatches(true, 0, "http://", 0, 7)
            || url.regionMatches(true, 0, "https://", 0, 8));
  }

  public static boolean isWellFormed(String url) {
    if (!is(url) || url.length() > MAX_LENGTH) return false;
    try {
      new URI(url).toURL();
      return true;
    } catch (URISyntaxException | MalformedURLException | IllegalArgumentException malformed) {
      return false;
    }
  }
}
