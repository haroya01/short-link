package com.example.short_link.link.application;

public final class IpMasker {

  private IpMasker() {}

  public static String mask(String ip) {
    if (ip == null || ip.isBlank()) return null;
    int colon = ip.indexOf(':');
    if (colon >= 0) {
      int second = ip.indexOf(':', colon + 1);
      if (second < 0) return ip;
      return ip.substring(0, second) + ":*:*:*:*:*:*";
    }
    int lastDot = ip.lastIndexOf('.');
    if (lastDot < 0) return ip;
    return ip.substring(0, lastDot) + ".*";
  }
}
