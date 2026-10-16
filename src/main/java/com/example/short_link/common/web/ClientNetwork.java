package com.example.short_link.common.web;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HexFormat;
import java.util.regex.Pattern;

public final class ClientNetwork {

  private static final Pattern IPV6_LITERAL = Pattern.compile("[0-9A-Fa-f.:]*:[0-9A-Fa-f.:]*");

  private ClientNetwork() {}

  public static String of(String clientIp) {
    if (clientIp == null) return null;
    int zone = clientIp.indexOf('%');
    String address = zone < 0 ? clientIp : clientIp.substring(0, zone);
    // Only a literal passes the pattern, so getByName never resolves a host name.
    if (!IPV6_LITERAL.matcher(address).matches()) return clientIp;
    try {
      InetAddress parsed = InetAddress.getByName(address);
      if (!(parsed instanceof Inet6Address)) return parsed.getHostAddress();
      return HexFormat.of().formatHex(parsed.getAddress(), 0, 8) + "::/64";
    } catch (UnknownHostException malformed) {
      return clientIp;
    }
  }
}
