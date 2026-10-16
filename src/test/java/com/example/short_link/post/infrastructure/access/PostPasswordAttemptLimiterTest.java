package com.example.short_link.post.infrastructure.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.counter.RedisWindowCounter;
import org.junit.jupiter.api.Test;

class PostPasswordAttemptLimiterTest {

  private final RedisWindowCounter counter = mock(RedisWindowCounter.class);
  private final PostPasswordAttemptLimiter limiter = new PostPasswordAttemptLimiter(counter);

  @Test
  void theTenthGuessInAWindowIsTheLastOneAllowed() {
    String key = "post-pwd-attempt:42:20010db800010002::/64";
    when(counter.increment(key, PostPasswordAttemptLimiter.WINDOW)).thenReturn(10L, 11L);

    assertThat(limiter.tryAttempt(42L, "2001:db8:1:2::9")).isTrue();
    assertThat(limiter.tryAttempt(42L, "2001:db8:1:2:ffff::1")).isFalse();
  }

  @Test
  void retryAfterRoundsTheRemainingWindowUpToWholeSeconds() {
    when(counter.remainingMillis("post-pwd-attempt:42:203.0.113.9")).thenReturn(1_001L, 0L);

    assertThat(limiter.retryAfterSeconds(42L, "203.0.113.9")).isEqualTo(2);
    assertThat(limiter.retryAfterSeconds(42L, "203.0.113.9")).isEqualTo(900);
  }

  @Test
  void aRightPasswordClearsTheCount() {
    limiter.reset(42L, "203.0.113.9");

    verify(counter).reset("post-pwd-attempt:42:203.0.113.9");
  }
}
