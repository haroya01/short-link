package com.example.short_link.link.application;

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
}
