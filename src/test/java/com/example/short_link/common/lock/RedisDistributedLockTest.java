package com.example.short_link.common.lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RedisDistributedLockTest {

  @Mock private StringRedisTemplate redis;
  @Mock private ValueOperations<String, String> ops;

  private RedisDistributedLock lock;

  @BeforeEach
  void setUp() {
    lock = new RedisDistributedLock(redis);
  }

  @Test
  void tryAcquireReturnsTrueWhenSetNxSucceeds() {
    when(redis.opsForValue()).thenReturn(ops);
    when(ops.setIfAbsent(eq("k"), eq("1"), any(Duration.class))).thenReturn(true);
    assertThat(lock.tryAcquire("k", Duration.ofSeconds(10))).isTrue();
  }

  @Test
  void tryAcquireReturnsFalseWhenSetNxFails() {
    when(redis.opsForValue()).thenReturn(ops);
    when(ops.setIfAbsent(eq("k"), eq("1"), any(Duration.class))).thenReturn(false);
    assertThat(lock.tryAcquire("k", Duration.ofSeconds(10))).isFalse();
  }

  @Test
  void tryAcquireReturnsFalseWhenSetNxReturnsNull() {
    when(redis.opsForValue()).thenReturn(ops);
    when(ops.setIfAbsent(eq("k"), eq("1"), any(Duration.class))).thenReturn(null);
    assertThat(lock.tryAcquire("k", Duration.ofSeconds(10))).isFalse();
  }

  @Test
  void releaseDelegatesToDelete() {
    lock.release("k");
    verify(redis).delete("k");
  }
}
