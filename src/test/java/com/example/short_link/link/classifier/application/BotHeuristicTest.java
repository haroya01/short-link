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
    BotHeuristic zero = new BotHeuristic((key, ttl) -> 10L, 0L);
    assertThat(zero.isSuspectBurst("9.9.9.9")).isFalse();

    BotHeuristic negative = new BotHeuristic((key, ttl) -> 10L, -1L);
    assertThat(negative.isSuspectBurst("9.9.9.9")).isFalse();
  }

  @Test
  void counterExceptionFailsOpen() {
    BotHeuristic h =
        new BotHeuristic(
            (key, ttl) -> {
              throw new RuntimeException("redis down");
            },
            5L);

    assertThat(h.isSuspectBurst("1.2.3.4")).isFalse();
  }

  @Test
  void nullCountIsNotSuspect() {
    BotHeuristic h = new BotHeuristic((key, ttl) -> null, 5L);

    assertThat(h.isSuspectBurst("1.2.3.4")).isFalse();
  }
}
