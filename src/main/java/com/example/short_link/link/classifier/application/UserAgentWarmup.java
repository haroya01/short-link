package com.example.short_link.link.classifier.application;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// The analyzer builds its data structures on the first parse, under the lock every parse takes. A
// redirect that arrives while this runs waits for it instead of paying for it alone.
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "short-link.user-agent",
    name = "warm-up",
    havingValue = "true",
    matchIfMissing = true)
class UserAgentWarmup {

  static final List<String> AGENTS =
      List.of(
          "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15"
              + " (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1",
          "Mozilla/5.0 (Linux; Android 14; SM-S918N) AppleWebKit/537.36 (KHTML, like Gecko)"
              + " Chrome/128.0.0.0 Mobile Safari/537.36",
          "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko)"
              + " Chrome/129.0.0.0 Safari/537.36",
          "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)");

  private final UserAgentClassifier classifier;

  @EventListener(ApplicationReadyEvent.class)
  void started() {
    Thread.ofPlatform().name("ua-warmup").daemon().start(this::warm);
  }

  void warm() {
    try {
      long started = System.nanoTime();
      AGENTS.forEach(classifier::classify);
      log.info("user agent analyzer warm in {} ms", (System.nanoTime() - started) / 1_000_000);
    } catch (RuntimeException failed) {
      log.warn("user agent analyzer warm-up failed reason={}", failed.toString());
    }
  }
}
