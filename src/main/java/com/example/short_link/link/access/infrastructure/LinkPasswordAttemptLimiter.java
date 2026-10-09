package com.example.short_link.link.access.infrastructure;

import com.example.short_link.common.counter.RedisWindowCounter;
import com.example.short_link.link.access.application.PasswordAttempts;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// Limits guesses per link and client network because the global per-IP limit is too loose for
// password brute force. Attempts since the last success count; success resets the counter.
@Component
@RequiredArgsConstructor
public class LinkPasswordAttemptLimiter implements PasswordAttempts {

  static final int MAX_ATTEMPTS = 10;
  private static final Duration WINDOW = Duration.ofMinutes(15);
  private static final Pattern IPV6_LITERAL = Pattern.compile("[0-9A-Fa-f.:]*:[0-9A-Fa-f.:]*");

  private final RedisWindowCounter counter;

  public boolean tryAttempt(String shortCode, String clientIp) {
    return counter.increment(key(shortCode, clientIp), WINDOW) <= MAX_ATTEMPTS;
  }

  public void reset(String shortCode, String clientIp) {
    counter.reset(key(shortCode, clientIp));
  }

  private static String key(String shortCode, String clientIp) {
    return "pwd-attempt:" + shortCode + ":" + network(clientIp);
  }

  static String network(String clientIp) {
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
