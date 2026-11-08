package com.example.short_link.common.net;

import java.io.ByteArrayOutputStream;
import java.net.IDN;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Locale;

public final class BrowserHost {

  private static final String AUTHORITY_ENDS = "/\\?#";

  private BrowserHost() {}

  public static String of(String url) {
    if (url == null) return null;
    String input = url.replaceAll("[\\t\\n\\r]", "").strip();
    int colon = input.indexOf(':');
    if (colon < 0) return null;
    String scheme = input.substring(0, colon).toLowerCase(Locale.ROOT);
    if (!scheme.equals("http") && !scheme.equals("https")) return null;
    int start = colon + 1;
    while (start < input.length() && isSlash(input.charAt(start))) start++;
    int end = start;
    while (end < input.length() && AUTHORITY_ENDS.indexOf(input.charAt(end)) < 0) end++;
    String authority = input.substring(start, end);
    String hostAndPort = authority.substring(authority.lastIndexOf('@') + 1);
    String host = withoutPort(hostAndPort);
    if (host.isEmpty()) return null;
    if (host.startsWith("[")) return host.toLowerCase(Locale.ROOT);
    String ascii = toAscii(percentDecoded(host)).toLowerCase(Locale.ROOT);
    int last = ascii.length();
    while (last > 0 && ascii.charAt(last - 1) == '.') last--;
    return last == 0 ? null : ascii.substring(0, last);
  }

  private static boolean isSlash(char c) {
    return c == '/' || c == '\\';
  }

  private static String withoutPort(String hostAndPort) {
    if (hostAndPort.startsWith("[")) {
      int close = hostAndPort.indexOf(']');
      return close < 0 ? hostAndPort : hostAndPort.substring(0, close + 1);
    }
    int portColon = hostAndPort.lastIndexOf(':');
    return portColon < 0 ? hostAndPort : hostAndPort.substring(0, portColon);
  }

  private static String percentDecoded(String host) {
    if (host.indexOf('%') < 0) return host;
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    byte[] raw = host.getBytes(StandardCharsets.UTF_8);
    for (int i = 0; i < raw.length; i++) {
      if (raw[i] == '%' && i + 2 < raw.length && isHex(raw[i + 1]) && isHex(raw[i + 2])) {
        bytes.write(HexFormat.fromHexDigits(new String(raw, i + 1, 2, StandardCharsets.US_ASCII)));
        i += 2;
      } else {
        bytes.write(raw[i]);
      }
    }
    return bytes.toString(StandardCharsets.UTF_8);
  }

  private static boolean isHex(byte b) {
    return HexFormat.isHexDigit(b);
  }

  private static String toAscii(String host) {
    try {
      return IDN.toASCII(host, IDN.ALLOW_UNASSIGNED);
    } catch (IllegalArgumentException notADomain) {
      return host;
    }
  }
}
