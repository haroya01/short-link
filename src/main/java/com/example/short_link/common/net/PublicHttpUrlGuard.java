package com.example.short_link.common.net;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

// Rejects non-HTTP(S) URLs and hosts resolving to non-public IPs. isPublic(String) alone is
// vulnerable to DNS rebinding: outbound callers must use resolve(String), connect to its returned
// IPs, and preserve the original Host header.
public final class PublicHttpUrlGuard {

  private PublicHttpUrlGuard() {}

  public static boolean isPublic(String url) {
    return resolve(url).isPresent();
  }

  // Rejects the URL if any resolved IP is private. Connect to a returned address without
  // re-resolving the host to prevent DNS rebinding.
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

  public static boolean hostExists(String url) {
    try {
      String host = URI.create(url).getHost();
      if (host == null || host.isBlank()) return true;
      InetAddress.getAllByName(host);
      return true;
    } catch (UnknownHostException e) {
      return false;
    } catch (IllegalArgumentException e) {
      return true;
    }
  }

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
      return isCarrierGradeNat(v4) || isReservedIpv4(v4);
    }
    if (addr instanceof Inet6Address v6) {
      return isIpv6UniqueLocal(v6) || isLocalNat64(v6) || carriesPrivateIpv4(v6);
    }
    return false;
  }

  private static boolean isReservedIpv4(Inet4Address v4) {
    byte[] b = v4.getAddress();
    int b0 = b[0] & 0xff;
    int b1 = b[1] & 0xff;
    return b0 == 0 || b0 >= 240 || (b0 == 198 && (b1 & 0xfe) == 18);
  }

  private static boolean isLocalNat64(Inet6Address v6) {
    byte[] b = v6.getAddress();
    return isNat64(b) && b[4] == 0 && b[5] == 1;
  }

  private static boolean carriesPrivateIpv4(Inet6Address v6) {
    byte[] b = v6.getAddress();
    if ((b[0] & 0xff) == 0x20 && b[1] == 0x02) {
      return isPrivateIpv4(Arrays.copyOfRange(b, 2, 6));
    }
    if (zeros(b, 4, 12) && (zeros(b, 0, 4) || isNat64(b))) {
      return isPrivateIpv4(Arrays.copyOfRange(b, 12, 16));
    }
    return isIpv4MappedPrivate(v6);
  }

  private static boolean isNat64(byte[] b) {
    return b[0] == 0 && b[1] == 0x64 && (b[2] & 0xff) == 0xff && (b[3] & 0xff) == 0x9b;
  }

  private static boolean isPrivateIpv4(byte[] address) {
    try {
      return isPrivate(InetAddress.getByAddress(address));
    } catch (UnknownHostException e) {
      return true;
    }
  }

  private static boolean zeros(byte[] b, int from, int to) {
    for (int i = from; i < to; i++) {
      if (b[i] != 0) return false;
    }
    return true;
  }

  private static boolean isCarrierGradeNat(Inet4Address v4) {
    byte[] b = v4.getAddress();
    int b0 = b[0] & 0xff;
    int b1 = b[1] & 0xff;
    return b0 == 100 && (b1 & 0xc0) == 64;
  }

  private static boolean isIpv6UniqueLocal(Inet6Address v6) {
    byte[] b = v6.getAddress();
    return (b[0] & 0xfe) == 0xfc;
  }

  // ::ffff:0:0/96 IPv4-mapped IPv6 — Java sometimes returns these for dual-stack hosts. Unwrap and
  // re-check against the IPv4 rules so a mapped 10.0.0.1 doesn't sneak past.
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
