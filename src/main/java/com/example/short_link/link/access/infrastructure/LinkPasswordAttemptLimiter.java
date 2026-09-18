package com.example.short_link.link.access.infrastructure;

import com.example.short_link.common.counter.RedisWindowCounter;
import com.example.short_link.link.access.application.PasswordAttempts;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Limits guesses per link and client IP because the global per-IP limit is too loose for password
 * brute force. Only password failures count; success resets the counter.
 */
@Component
@RequiredArgsConstructor
public class LinkPasswordAttemptLimiter implements PasswordAttempts {

  static final int MAX_FAILURES = 10;
  private static final Duration WINDOW = Duration.ofMinutes(15);

  private final RedisWindowCounter counter;

  public boolean isLockedOut(String shortCode, String clientIp) {
    return counter.current(key(shortCode, clientIp), WINDOW) >= MAX_FAILURES;
  }

  public void recordFailure(String shortCode, String clientIp) {
    counter.increment(key(shortCode, clientIp), WINDOW);
  }

  public void reset(String shortCode, String clientIp) {
    counter.reset(key(shortCode, clientIp));
  }

  private static String key(String shortCode, String clientIp) {
    return "pwd-attempt:" + shortCode + ":" + clientIp;
  }
}
