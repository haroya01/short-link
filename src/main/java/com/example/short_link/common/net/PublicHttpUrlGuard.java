package com.example.short_link.common.net;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Rejects non-HTTP(S) URLs and hosts resolving to non-public IPs. {@link #isPublic(String)} alone
 * is vulnerable to DNS rebinding: outbound callers must use {@link #resolve(String)}, connect to
 * its returned IPs, and preserve the original Host header.
 */
public final class PublicHttpUrlGuard {

  private PublicHttpUrlGuard() {}

  public static boolean isPublic(String url) {
    return resolve(url).isPresent();
  }

  /**
   * Rejects the URL if any resolved IP is private. Connect to a returned address without
   * re-resolving the host to prevent DNS rebinding.
   */
  public static Optional<Resolved> resolve(String url) {
    if (url == null || url.isBlank()) return Optional.empty();
    URI uri;
    try {
      uri = URI.create(url);
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
    String scheme = uri.getScheme();
    if (scheme == null) return Optional.empty();
    String s = scheme.toLowerCase(Locale.ROOT);
    if (!s.equals("http") && !s.equals("https")) return Optional.empty();
    String host = uri.getHost();
    if (host == null || host.isBlank()) return Optional.empty();
    InetAddress[] addrs;
    try {
      addrs = InetAddress.getAllByName(host);
    } catch (UnknownHostException e) {
      return Optional.empty();
    }
    for (InetAddress addr : addrs) {
      if (isPrivate(addr)) return Optional.empty();
    }
    return Optional.of(new Resolved(uri, List.of(addrs)));
  }

  /**
   * All IPs resolved at validation time. Connect directly to one of them with the original Host
   * header.
   */
  public record Resolved(URI uri, List<InetAddress> addresses) {}

  static boolean isPrivate(InetAddress addr) {
    if (addr.isLoopbackAddress()
        || addr.isLinkLocalAddress()
        || addr.isSiteLocalAddress()
        || addr.isAnyLocalAddress()
        || addr.isMulticastAddress()) {
      return true;
    }
    if (addr instanceof Inet4Address v4) {
      return isCarrierGradeNat(v4);
    }
    if (addr instanceof Inet6Address v6) {
      return isIpv6UniqueLocal(v6) || isIpv4MappedPrivate(v6);
    }
    return false;
  }

  /** RFC 6598 — 100.64.0.0/10 — shared CGNAT space, treated as non-public. */
  private static boolean isCarrierGradeNat(Inet4Address v4) {
    byte[] b = v4.getAddress();
    int b0 = b[0] & 0xff;
    int b1 = b[1] & 0xff;
    return b0 == 100 && (b1 & 0xc0) == 64;
  }

  /** RFC 4193 — fc00::/7 (high bits 1111 110x) — IPv6 unique-local addresses. */
  private static boolean isIpv6UniqueLocal(Inet6Address v6) {
    byte[] b = v6.getAddress();
    return (b[0] & 0xfe) == 0xfc;
  }

  /**
   * ::ffff:0:0/96 IPv4-mapped IPv6 — Java sometimes returns these for dual-stack hosts. Unwrap and
   * re-check against the IPv4 rules so a mapped 10.0.0.1 doesn't sneak past.
   */
  private static boolean isIpv4MappedPrivate(Inet6Address v6) {
    byte[] b = v6.getAddress();
    for (int i = 0; i < 10; i++) {
      if (b[i] != 0) return false;
    }
    if ((b[10] & 0xff) != 0xff || (b[11] & 0xff) != 0xff) return false;
    try {
      InetAddress unwrapped = InetAddress.getByAddress(new byte[] {b[12], b[13], b[14], b[15]});
      return isPrivate(unwrapped);
    } catch (UnknownHostException e) {
      return true;
    }
  }
}
