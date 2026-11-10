package com.example.short_link.link.webhook.domain;

import com.example.short_link.common.webhook.WebhookSender;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public final class WebhookRetryPolicy {

  public static final int MAX_ATTEMPTS = 6;
  private static final Duration FIRST_WAIT = Duration.ofSeconds(30);
  private static final Duration LONGEST_WAIT = Duration.ofHours(1);

  private WebhookRetryPolicy() {}

  public static boolean retryable(WebhookSender.Result result) {
    return switch (result.outcome()) {
      case OK, BLOCKED, SIGN_ERROR -> false;
      case EXCEPTION -> true;
      case NON_2XX -> {
        Integer status = result.statusCode();
        yield status == null || status >= 500 || status == 408 || status == 425 || status == 429;
      }
    };
  }

  public static Optional<Instant> nextAttempt(int attemptsSoFar, Instant now) {
    if (attemptsSoFar >= MAX_ATTEMPTS) return Optional.empty();
    Duration wait = FIRST_WAIT.multipliedBy(1L << Math.max(0, attemptsSoFar - 1));
    return Optional.of(now.plus(wait.compareTo(LONGEST_WAIT) > 0 ? LONGEST_WAIT : wait));
  }
}
