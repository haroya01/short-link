package com.example.short_link.common.net;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * Shared SSRF / private-IP guard. Used anywhere we make outbound HTTP from user-supplied URLs — OG
 * fetcher, click webhooks, etc. Rejects schemes other than http/https, missing host, and any host
 * whose DNS resolution lands on a loopback / link-local / site-local / multicast address.
 */
public final class PublicHttpUrlGuard {

  private PublicHttpUrlGuard() {}

  public static boolean isPublic(String url) {
    if (url == null || url.isBlank()) return false;
    URI uri;
    try {
      uri = URI.create(url);
    } catch (IllegalArgumentException e) {
      return false;
    }
    String scheme = uri.getScheme();
    if (scheme == null) return false;
    String s = scheme.toLowerCase(Locale.ROOT);
    if (!s.equals("http") && !s.equals("https")) return false;
    String host = uri.getHost();
    if (host == null || host.isBlank()) return false;
    try {
      for (InetAddress addr : InetAddress.getAllByName(host)) {
        if (isPrivateOrLoopback(addr)) return false;
      }
    } catch (UnknownHostException e) {
      return false;
    }
    return true;
  }

  private static boolean isPrivateOrLoopback(InetAddress addr) {
    return addr.isLoopbackAddress()
        || addr.isLinkLocalAddress()
        || addr.isSiteLocalAddress()
        || addr.isAnyLocalAddress()
        || addr.isMulticastAddress();
  }
}
