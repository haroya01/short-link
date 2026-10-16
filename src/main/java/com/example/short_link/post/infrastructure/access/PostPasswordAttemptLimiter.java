package com.example.short_link.post.infrastructure.access;

import com.example.short_link.common.counter.RedisWindowCounter;
import com.example.short_link.common.web.ClientNetwork;
import com.example.short_link.post.application.access.PostPasswordAttempts;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class PostPasswordAttemptLimiter implements PostPasswordAttempts {

  static final int MAX_ATTEMPTS = 10;
  static final Duration WINDOW = Duration.ofMinutes(15);

  private final RedisWindowCounter counter;

  @Override
  public boolean tryAttempt(Long postId, String clientIp) {
    return counter.increment(key(postId, clientIp), WINDOW) <= MAX_ATTEMPTS;
  }

  @Override
  public long retryAfterSeconds(Long postId, String clientIp) {
    long millis = counter.remainingMillis(key(postId, clientIp));
    return millis <= 0 ? WINDOW.toSeconds() : Math.max(1, (millis + 999) / 1000);
  }

  @Override
  public void reset(Long postId, String clientIp) {
    counter.reset(key(postId, clientIp));
  }

  private static String key(Long postId, String clientIp) {
    return "post-pwd-attempt:" + postId + ":" + ClientNetwork.of(clientIp);
  }
}
