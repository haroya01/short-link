package com.example.short_link.link.classifier.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "short-link.bot-heuristic.rate-threshold=3")
class BotHeuristicTest {

  @Autowired private BotHeuristic heuristic;
  @Autowired private StringRedisTemplate redis;

  @Test
  void flagsAfterThresholdReached() {
    String ip = "10.20.30." + System.nanoTime();
    redis.delete("bot:click:ip:" + ip);
    assertThat(heuristic.isSuspectBurst(ip)).isFalse();
    assertThat(heuristic.isSuspectBurst(ip)).isFalse();
    assertThat(heuristic.isSuspectBurst(ip)).isTrue();
    assertThat(heuristic.isSuspectBurst(ip)).isTrue();
  }

  @Test
  void doesNotFlagOnNullOrBlankIp() {
    assertThat(heuristic.isSuspectBurst(null)).isFalse();
    assertThat(heuristic.isSuspectBurst("")).isFalse();
    assertThat(heuristic.isSuspectBurst("  ")).isFalse();
  }

  @Test
  void disabledWhenThresholdIsZeroOrNegative() {
    BotHeuristic zero = new BotHeuristic(redis, 0L);
    assertThat(zero.isSuspectBurst("9.9.9.9")).isFalse();

    BotHeuristic negative = new BotHeuristic(redis, -1L);
    assertThat(negative.isSuspectBurst("9.9.9.9")).isFalse();
  }

  @Test
  void redisExceptionFailsOpen() {
    StringRedisTemplate template = org.mockito.Mockito.mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    org.springframework.data.redis.core.ValueOperations<String, String> ops =
        org.mockito.Mockito.mock(org.springframework.data.redis.core.ValueOperations.class);
    org.mockito.Mockito.when(template.opsForValue()).thenReturn(ops);
    org.mockito.Mockito.when(ops.increment(org.mockito.ArgumentMatchers.anyString()))
        .thenThrow(new RuntimeException("redis down"));
    BotHeuristic h = new BotHeuristic(template, 5L);

    assertThat(h.isSuspectBurst("1.2.3.4")).isFalse();
  }

  @Test
  void redisReturnsNullCountIsNotSuspect() {
    StringRedisTemplate template = org.mockito.Mockito.mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    org.springframework.data.redis.core.ValueOperations<String, String> ops =
        org.mockito.Mockito.mock(org.springframework.data.redis.core.ValueOperations.class);
    org.mockito.Mockito.when(template.opsForValue()).thenReturn(ops);
    org.mockito.Mockito.when(ops.increment(org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(null);
    BotHeuristic h = new BotHeuristic(template, 5L);

    assertThat(h.isSuspectBurst("1.2.3.4")).isFalse();
  }
}
