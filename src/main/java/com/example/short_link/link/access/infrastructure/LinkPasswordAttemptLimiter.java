package com.example.short_link.link.access.infrastructure;

import com.example.short_link.common.counter.RedisWindowCounter;
import com.example.short_link.common.web.ClientNetwork;
import com.example.short_link.link.access.application.PasswordAttempts;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// Limits guesses per link and client network because the global per-IP limit is too loose for
// password brute force. Attempts since the last success count; success resets the counter.
@Component
@RequiredArgsConstructor
public class LinkPasswordAttemptLimiter implements PasswordAttempts {

  static final int MAX_ATTEMPTS = 10;
  private static final Duration WINDOW = Duration.ofMinutes(15);

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
    return ClientNetwork.of(clientIp);
  }
}
