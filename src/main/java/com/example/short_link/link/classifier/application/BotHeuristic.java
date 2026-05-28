package com.example.short_link.link.classifier.application;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BotHeuristic {

  private static final String KEY_PREFIX = "bot:click:ip:";
  private static final Duration WINDOW = Duration.ofSeconds(1);
  public static final String SUSPECT_LABEL = "rate-flood";

  private final BurstCounter burstCounter;
  private final long rateThreshold;

  public BotHeuristic(
      BurstCounter burstCounter,
      @Value("${short-link.bot-heuristic.rate-threshold:5}") long rateThreshold) {
    this.burstCounter = burstCounter;
    this.rateThreshold = rateThreshold;
  }

  public boolean isSuspectBurst(String clientIp) {
    if (clientIp == null || clientIp.isBlank()) return false;
    if (rateThreshold <= 0) return false;
    try {
      Long count = burstCounter.increment(KEY_PREFIX + clientIp, WINDOW);
      return count != null && count >= rateThreshold;
    } catch (RuntimeException e) {
      log.warn("bot heuristic redis call failed", e);
      return false;
    }
  }
}
