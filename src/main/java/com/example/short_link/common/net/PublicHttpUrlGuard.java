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
 * Shared SSRF / private-IP guard. Used anywhere we make outbound HTTP from user-supplied URLs — OG
 * fetcher, click webhooks, etc. Rejects schemes other than http/https, missing host, and any host
 * whose DNS resolution lands on a loopback / link-local / site-local (RFC1918) / multicast /
 * carrier-grade NAT (RFC6598) / IPv6 unique-local (fc00::/7) address.
 *
 * <p>The {@link #isPublic(String)} boolean form is convenient but TOCTOU-unsafe: a malicious DNS
 * server can return a public IP at validation time and a private one at fetch time (DNS rebinding).
 * For real outbound HTTP, callers should use {@link #resolve(String)} and connect directly to the
 * returned IP, preserving the Host header from the original URL.
 */
public final class PublicHttpUrlGuard {

  private PublicHttpUrlGuard() {}

  public static boolean isPublic(String url) {
    return resolve(url).isPresent();
  }

  /**
   * Parse, scheme-check, and resolve the URL's host. Returns the resolved addresses (all of them —
   * if any one is private the URL is rejected outright). Callers that actually open a connection
   * should use the resolved address to connect, not re-resolve the host, to close the DNS rebinding
   * window.
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
   * Resolution result paired with the URI that produced it. {@code addresses} contains every IP the
   * host resolved to at validation time — outbound clients should connect to one of these directly
   * (with the original Host header) rather than re-resolving.
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
