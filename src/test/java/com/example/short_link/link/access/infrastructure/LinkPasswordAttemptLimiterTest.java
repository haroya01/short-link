package com.example.short_link.link.access.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.counter.RedisWindowCounter;
import org.junit.jupiter.api.Test;

class LinkPasswordAttemptLimiterTest {

  private final RedisWindowCounter counter = mock(RedisWindowCounter.class);
  private final LinkPasswordAttemptLimiter limiter = new LinkPasswordAttemptLimiter(counter);

  @Test
  void anAttemptIsAllowedUpToTheLimitAndRefusedAfterIt() {
    when(counter.increment(eq("pwd-attempt:abc123:203.0.113.9"), any()))
        .thenReturn(10L)
        .thenReturn(11L);

    assertThat(limiter.tryAttempt("abc123", "203.0.113.9")).isTrue();
    assertThat(limiter.tryAttempt("abc123", "203.0.113.9")).isFalse();
  }

  @Test
  void ipv6AddressesAreGroupedByTheirSlash64() {
    assertThat(LinkPasswordAttemptLimiter.network("2001:db8:1:2::1"))
        .isEqualTo(LinkPasswordAttemptLimiter.network("2001:db8:1:2:ffff:ffff:ffff:ffff"))
        .isEqualTo(LinkPasswordAttemptLimiter.network("2001:0DB8:0001:0002:0:0:0:abcd%en0"))
        .isEqualTo("20010db800010002::/64");
    assertThat(LinkPasswordAttemptLimiter.network("2001:db8:1:3::1"))
        .isEqualTo("20010db800010003::/64");
  }

  @Test
  void ipv4AndUnparseableAddressesKeepTheirOwnKey() {
    assertThat(LinkPasswordAttemptLimiter.network("203.0.113.9")).isEqualTo("203.0.113.9");
    assertThat(LinkPasswordAttemptLimiter.network("::ffff:203.0.113.9")).isEqualTo("203.0.113.9");
    assertThat(LinkPasswordAttemptLimiter.network("not:an:address")).isEqualTo("not:an:address");
    assertThat(LinkPasswordAttemptLimiter.network("1:2:3")).isEqualTo("1:2:3");
    assertThat(LinkPasswordAttemptLimiter.network(null)).isNull();
  }

  @Test
  void resettingClearsTheCounterOfTheWholeSlash64() {
    limiter.reset("abc123", "2001:db8:1:2::99");

    verify(counter).reset("pwd-attempt:abc123:20010db800010002::/64");
  }
}
