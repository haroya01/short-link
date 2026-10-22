package com.example.short_link.link.domain;

public final class WebAddress {

  private WebAddress() {}

  public static boolean is(String url) {
    return url != null
        && (url.regionMatches(true, 0, "http://", 0, 7)
            || url.regionMatches(true, 0, "https://", 0, 8));
  }
}
